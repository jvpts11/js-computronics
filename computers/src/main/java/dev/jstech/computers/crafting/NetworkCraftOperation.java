/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.crafting;

import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.operation.NetworkIndex;
import dev.jstech.computers.operation.NetworkStorage;
import dev.jstech.computers.operation.IPersistentOperation;
import dev.jstech.computers.operation.index.Allocation;
import dev.jstech.computers.operation.payload.OperationRecord;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.core.operation.OperationPriority;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A multi-tick CRAFT: executes a {@link CraftPlanner.Plan} on a Crafting Computer.
 */
public final class NetworkCraftOperation implements IPersistentOperation {

    public static final String KIND = "craft";

    @Override
    public CompoundTag saveState(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putString(KIND_KEY, KIND);
        tag.putUUID(ID_KEY, operationId);
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        StorageKey.CODEC.encodeStart(ops, resultKey).result().ifPresent(t -> tag.put("Result", t));
        tag.putLong("Requested", requested);
        tag.putString("Label", requesterLabel);
        tag.putByte(PRIORITY_KEY, (byte) priority.ordinal());
        if (embeddedPattern != null) {
            CraftingPattern.CODEC.encodeStart(ops, embeddedPattern).result().ifPresent(t -> tag.put("Embedded", t));
        }
        /*
         * Every machine step still running keeps going on its own after a reload; the re-planned craft waits for
         * them all so their output is in stock when the plan is made, instead of being made a second time.
         */
        final ListTag machineSteps = new ListTag();
        for (final MachineRun run : machineRuns) {
            if (!run.op.isDone()) {
                final CompoundTag row = new CompoundTag();
                row.putUUID("Id", run.op.operationId());
                machineSteps.add(row);
            }
        }
        if (!machineSteps.isEmpty()) {
            tag.put(MACHINE_STEPS_KEY, machineSteps);
        }
        /*
         * Everything this craft has drained from the network but not delivered yet: intermediates and
         * finished results alike. They are handed back to storage on resume, so nothing is lost or doubled.
         */
        final ListTag pool = new ListTag();
        for (final Map.Entry<StorageKey, Long> entry : this.pool.entrySet()) {
            if (entry.getValue() > 0) {
                StorageKey.CODEC.encodeStart(ops, entry.getKey()).result().ifPresent(keyTag -> {
                    final CompoundTag row = new CompoundTag();
                    row.put("Key", keyTag);
                    row.putLong("Amount", entry.getValue());
                    pool.add(row);
                });
            }
        }
        tag.put("Pool", pool);
        return tag;
    }

    /**
     * The outcome of {@link #restore}: the operation now running the remaining demand, or none because the
     * request was already fully delivered from the items in flight ({@code complete}) or could not be
     * planned again ({@code complete} false, {@code operation} null).
     */
    public record Restored(@org.jetbrains.annotations.Nullable NetworkCraftOperation operation, boolean complete) {
    }

    /**
     * Resumes a craft saved by {@link #saveState}: the items it held in flight go back into network storage, and
     * the remaining demand is planned again as a fresh craft (reservations and computer claims live in RAM and
     * are gone after a reload, so re-planning is the honest way to continue).
     */
    public static Restored restore(final CompoundTag tag, final MainframeBlockEntity mainframe,
                                   final ServerLevel level, final NetworkUuid network,
                                   final HolderLookup.Provider registries) {
        return restore(tag, mainframe, level, network, registries, List.of());
    }

    /** The ids of the machine steps a saved craft still had running, empty if none. */
    public static List<UUID> savedMachineSteps(final CompoundTag tag) {
        final List<UUID> ids = new ArrayList<>();
        final ListTag steps = tag.getList(MACHINE_STEPS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < steps.size(); i++) {
            final CompoundTag row = steps.getCompound(i);
            if (row.hasUUID("Id")) {
                ids.add(row.getUUID("Id"));
            }
        }
        return ids;
    }

    /**
     * Like {@link #restore(CompoundTag, MainframeBlockEntity, ServerLevel, NetworkUuid, HolderLookup.Provider)},
     * but when the craft had machine steps in flight (restored and still running), the items it held go back to
     * storage now and the re-plan waits until every one of those steps settles, so their output counts as stock.
     * Such a craft is reported as not yet running ({@code operation} null, {@code complete} false).
     */
    public static Restored restore(final CompoundTag tag, final MainframeBlockEntity mainframe,
                                   final ServerLevel level, final NetworkUuid network,
                                   final HolderLookup.Provider registries,
                                   final List<NetworkProcessingOperation> machineSteps) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final StorageKey result = tag.contains("Result")
                ? StorageKey.CODEC.parse(ops, tag.get("Result")).result().orElse(null) : null;
        if (result == null) {
            return new Restored(null, false);
        }
        final NetworkStorage storage = NetworkStorage.of(level, network);
        long delivered = 0;
        final ListTag pool = tag.getList("Pool", Tag.TAG_COMPOUND);
        for (int i = 0; i < pool.size(); i++) {
            final CompoundTag row = pool.getCompound(i);
            final StorageKey key = StorageKey.CODEC.parse(ops, row.get("Key")).result().orElse(null);
            final long amount = row.getLong("Amount");
            if (key != null && amount > 0) {
                final long stored = storage.insert(key, amount);
                if (key.equals(result)) {
                    delivered += stored;
                }
            }
        }
        final long remaining = tag.getLong("Requested") - delivered;
        if (remaining <= 0) {
            return new Restored(null, true);
        }
        final CraftingPattern embedded = tag.contains("Embedded")
                ? CraftingPattern.CODEC.parse(ops, tag.get("Embedded")).result().orElse(null) : null;
        final String label = tag.getString("Label");
        final OperationPriority priority = savedPriority(tag);
        final List<NetworkProcessingOperation> running = new ArrayList<>();
        for (final NetworkProcessingOperation step : machineSteps) {
            if (step != null && !step.isDone()) {
                running.add(step);
            }
        }
        if (running.isEmpty()) {
            return new Restored(withPriority(
                    mainframe.submitNetworkCraft(result, remaining, true, label, embedded), priority), false);
        }
        // Re-plan a tick after the last in-flight step settles, once the storage index has seen what they made.
        final int[] pending = {running.size()};
        for (final NetworkProcessingOperation step : running) {
            step.onSettle(() -> {
                if (--pending[0] == 0) {
                    mainframe.runNextTick(() -> withPriority(
                            mainframe.submitNetworkCraft(result, remaining, true, label, embedded), priority));
                }
            });
        }
        return new Restored(null, false);
    }

    /** The scheduling level a saved operation ran at; the default for saves that predate priorities. */
    static OperationPriority savedPriority(final CompoundTag tag) {
        return tag.contains(PRIORITY_KEY)
                ? OperationPriority.byOrdinal(tag.getByte(PRIORITY_KEY)) : OperationPriority.DEFAULT;
    }

    @org.jetbrains.annotations.Nullable
    private static NetworkCraftOperation withPriority(
            @org.jetbrains.annotations.Nullable final NetworkCraftOperation operation,
            final OperationPriority priority) {
        if (operation != null) {
            operation.setPriority(priority);
        }
        return operation;
    }

    static final String PRIORITY_KEY = "Priority";

    private static final String MACHINE_STEPS_KEY = "MachineSteps";
    private static final int STALL_LIMIT = 100;

    /** The design default of the WAITING timeout; the live value comes from the balance config. */
    public static final int DEFAULT_WAIT_TIMEOUT_TICKS =
            dev.jstech.core.operation.OperationBalance.DEFAULT_WAITING_TIMEOUT_TICKS;

    private final ServerLevel level;
    private final NetworkUuid network;
    private final StorageKey resultKey;
    private final long requested;
    private final CraftPlanner.Plan plan;
    private final NetworkIndex index;
    private final UUID operationId;
    private final List<BlockPos> candidateComputers;
    private final List<BlockPos> supercomputers;
    private final String requesterLabel;
    /*
     * A pattern that travels with the request instead of living in a Recipe ROM: a multi-stage
     * pipeline's bench stage embeds its pattern, so any online computer may execute it.
     */
    @org.jetbrains.annotations.Nullable
    private final CraftingPattern embeddedPattern;

    private final Map<StorageKey, Long> pool = new HashMap<>();
    /*
     * The servers each ingredient's reservation was placed on, so the per-tick drain targets the same
     * servers and every release frees the matching reservation instead of silently missing.
     */
    private final Map<StorageKey, java.util.Set<NodeUuid>> lockedServers = new HashMap<>();
    private final long[] runsDone;

    /*
     * With a supercomputer, one request fans out across several CCs at once (one per granted slot); without
     * one, a single exclusively-claimed CC. The per-tick rate is the summed throughput of the live executors.
     */
    private final List<CraftingComputerBlockEntity> executors = new ArrayList<>();
    private dev.jstech.computers.blockentity.HbwInterfaceBlockEntity orchestrator;
    private boolean exclusiveClaim;
    private boolean locked;
    private boolean waiting = true;
    private int waitTicks;
    private boolean timedOut;
    private int stalledTicks;
    private long deliveredResult;
    private boolean done;
    private boolean cancelled;
    private byte status = OperationRecord.STATUS_FAILED;
    private OperationPriority priority = OperationPriority.DEFAULT;
    private Runnable onSettle;
    /*
     * The Mainframe that runs this craft's machine steps as processing operations of their own; null only for
     * plans without machine steps.
     */
    @org.jetbrains.annotations.Nullable
    private final MainframeBlockEntity mainframe;
    /*
     * The machine steps this craft is running right now, one processing operation each. A downstream step
     * launches as soon as its inputs reach the pool (raws are locked; an upstream step delivers its output
     * there), so a chain pipelines instead of running one stage at a time; the count is bounded by the executing
     * computer's crafting-thread ceiling. This is the Operations system's concurrent design applied WITHIN one craft.
     */
    private final List<MachineRun> machineRuns = new ArrayList<>();
    /*
     * Each machine step is launched at most once (its own operation feeds all of its runs); a step that fell
     * short because its machine broke or timed out is not retried.
     */
    private final boolean[] launched;
    /*
     * True while the claimed computer or cluster slots are let go during the machine steps (the machines, not
     * the computer, are working); the craft claims a computer again once every step clears and bench work remains.
     */
    private boolean executorsParked;

    /** One running machine step: which plan step it is and the processing operation executing it. */
    private static final class MachineRun {
        final int step;
        final NetworkProcessingOperation op;

        MachineRun(final int step, final NetworkProcessingOperation op) {
            this.step = step;
            this.op = op;
        }
    }

    public NetworkCraftOperation(final ServerLevel level, final NetworkUuid network,
                                 final StorageKey resultKey, final long requested,
                                 final CraftPlanner.Plan plan, final NetworkIndex index,
                                 final UUID operationId, final List<BlockPos> candidateComputers,
                                 final List<BlockPos> supercomputers, final String requesterLabel) {
        this(level, network, resultKey, requested, plan, index, operationId, candidateComputers,
                supercomputers, requesterLabel, null, null);
    }

    public NetworkCraftOperation(final ServerLevel level, final NetworkUuid network,
                                 final StorageKey resultKey, final long requested,
                                 final CraftPlanner.Plan plan, final NetworkIndex index,
                                 final UUID operationId, final List<BlockPos> candidateComputers,
                                 final List<BlockPos> supercomputers, final String requesterLabel,
                                 @org.jetbrains.annotations.Nullable final CraftingPattern embeddedPattern,
                                 @org.jetbrains.annotations.Nullable final MainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
        this.level = level;
        this.network = network;
        this.resultKey = resultKey;
        this.requested = requested;
        this.plan = plan;
        this.index = index;
        this.operationId = operationId;
        this.candidateComputers = List.copyOf(candidateComputers);
        this.supercomputers = List.copyOf(supercomputers);
        this.requesterLabel = requesterLabel;
        this.embeddedPattern = embeddedPattern;
        this.runsDone = new long[plan.steps().size()];
        this.launched = new boolean[plan.steps().size()];
        if (plan.steps().isEmpty()) {
            finish();
        }
    }

    @Override
    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        /*
         * Collect finished machine steps: their output was delivered to the network, so count what each made. A
         * step that fell short (its machine broke or timed out) is not retried; it contributes what it managed.
         */
        machineRuns.removeIf(run -> {
            if (!run.op.isDone()) {
                return false;
            }
            final CraftPlanner.Step step = plan.steps().get(run.step);
            runsDone[run.step] = Math.min(step.runs(), run.op.produced() / Math.max(1L, step.perRun()));
            return true;
        });

        if (waiting) {
            /*
             * Acquire in two stages, holding nothing while blocked so waiters cannot deadlock:
             * first the full ingredient reservation, then an idle computer that can execute. Distinct crafts are
             * gated by executor availability (a lone Crafting Computer serves one at a time; a Supercomputer
             * cluster unlocks parallel crafts), not by the Mainframe's operation queues, because crafting is a subnet.
             */
            if (++waitTicks > dev.jstech.core.operation.OperationBalance.waitingTimeoutTicks()) {
                timedOut = true;
                finish();
                return;
            }
            if (!locked && !tryLockIngredients()) {
                return;
            }
            if (!tryClaimExecutors()) {
                return;
            }
            waiting = false;
            executorsParked = false;
        }
        if (!executorsAlive()) {
            /*
             * The computer was broken or powered off mid-craft: settle with what was produced. Any machine step
             * already in flight is an operation of its own and runs on to deliver its output to the network, so
             * nothing it was making is lost; the craft simply stops launching new steps and doing bench work.
             */
            finish();
            return;
        }

        /*
         * Launch every machine step whose inputs are ready (raws are locked; an upstream step's output sits in
         * the pool), up to the executing computer's crafting-thread ceiling. Launching needs only a live computer
         * to exist, not the held claim, so a downstream step starts while an upstream step still runs and this
         * craft is parked: a chain pipelines and independent steps run at once, instead of one stage at a time.
         */
        boolean progressed = startReadyMachineSteps();

        if (allRunsDone() && machineRuns.isEmpty()) {
            finish();
            return;
        }

        if (!machineRuns.isEmpty()) {
            /*
             * The machines are doing the work: let go of the computer and the cluster slots so other crafts can
             * use them while this craft waits (it keeps a reference to the computer for the liveness check above),
             * and claim again once every step clears and bench work remains.
             */
            parkExecutors();
            progressed = true;
        } else {
            if (executorsParked) {
                executorsParked = false;
                waiting = true;
                waitTicks = 0;
                return; // re-claim a computer before the bench work resumes
            }
            /*
             * Bench work at the summed throughput of the live computers (never above the Mainframe's grant).
             * Denser recipes take longer; faster computers contribute proportionally more of each tick's work.
             */
            long budget = Math.min(throughputBudget, aliveThroughput());
            final NetworkStorage storage = NetworkStorage.of(level, network);
            for (int i = 0; i < plan.steps().size() && budget > 0; i++) {
                final CraftPlanner.Step step = plan.steps().get(i);
                if (step.isMachine() || runsDone[i] >= step.runs()) {
                    continue;
                }
                final long unitsPerRun = step.unitsPerRun();
                final long runsAffordable = Math.max(budget >= unitsPerRun ? budget / unitsPerRun : 0, 0);
                if (runsAffordable <= 0) {
                    continue;
                }
                final long runsNow = Math.min(step.runs() - runsDone[i], runsAffordable);
                /*
                 * A bench step consumes its machine-made intermediates straight from the pool (a machine step
                 * delivered them there) plus its locked raw stock; its result goes back into the pool.
                 */
                final long executable = consumeIngredients(storage, step.pattern(), runsNow);
                if (executable <= 0) {
                    continue; // ingredients not deliverable yet (an upstream step must fill the pool first)
                }
                pool.merge(StorageKey.of(step.pattern().result()),
                        executable * step.pattern().result().getCount(), Long::sum);
                runsDone[i] += executable;
                budget -= executable * unitsPerRun;
                progressed = true;
            }
        }

        if (allRunsDone()) {
            if (machineRuns.isEmpty()) {
                finish();
            }
        } else if (!progressed && ++stalledTicks >= STALL_LIMIT) {
            finish();
        } else if (progressed) {
            stalledTicks = 0;
        }
    }

    /**
     * Starts a processing operation for every machine step whose inputs are ready, up to this craft's stage
     * ceiling. A step is ready when the pool holds at least one run of each of its intermediate inputs (raws stay
     * locked and available); each step is launched at most once, feeds from and delivers to this craft's isolated
     * pool through {@link #poolIo()}, so a chain pipelines through the pool without ever putting an intermediate
     * into shared network storage (which would confuse another craft's planner) and independent steps run at once.
     * The ceiling is the executing computers' summed crafting-thread count, not the Mainframe's operation queues:
     * a craft orchestrates its own stages with its Crafting Computer's hardware, independent of how many distinct
     * operations the Mainframe runs at once.
     */
    private boolean startReadyMachineSteps() {
        if (mainframe == null) {
            return !machineRuns.isEmpty();
        }
        final int cap = Math.max(1, aliveThreads());
        for (int i = 0; i < plan.steps().size() && machineRuns.size() < cap; i++) {
            final CraftPlanner.Step step = plan.steps().get(i);
            if (!step.isMachine() || launched[i] || runsDone[i] >= step.runs() || !machineInputsReady(step)) {
                continue;
            }
            final NetworkProcessingOperation op =
                    mainframe.submitNetworkProcessing(step.machine(), step.produced(), requesterLabel, poolIo());
            if (op == null) {
                continue;
            }
            op.setPriority(priority); // a stage of this craft reports the craft's level
            machineRuns.add(new MachineRun(i, op));
            launched[i] = true;
        }
        return !machineRuns.isEmpty();
    }

    /**
     * Whether the pool already holds at least one run of each of {@code step}'s non-raw inputs. Raw inputs are
     * drawn from the servers this craft locked (by the pool ICraftIo) and are available while the reservation
     * holds; an intermediate must have been produced into the pool by an upstream step first.
     */
    private boolean machineInputsReady(final CraftPlanner.Step step) {
        for (final ProcessingPattern.ProcessingInput in : step.machine().inputs()) {
            if (lockedServers.containsKey(in.key())) {
                continue; // a raw served from the locked servers
            }
            if (pool.getOrDefault(in.key(), 0L) < in.amount()) {
                return false; // an intermediate not yet produced by an upstream step
            }
        }
        return true;
    }

    /**
     * This craft's isolated I/O for a machine step: inputs come from the pool (intermediates an upstream step
     * produced) or from the servers this craft locked (raws, extracted scoped to those servers and released as
     * they leave, race-free, the way the bench path already consumes); outputs go straight into the pool. So the
     * craft's intermediates never touch shared network storage while it runs, which keeps concurrent steps
     * pipelining without racing and keeps other crafts' planners from ever seeing a half-made intermediate.
     *
     * <p>Once the craft has settled, the step is on its own (a computer died, or the craft timed out): it then
     * reads from and writes to the network, so a machine already in motion still delivers its output to storage
     * instead of into a pool nothing hands back.
     */
    private ICraftIo poolIo() {
        return new ICraftIo() {
            @Override
            public long select(final StorageKey key, final long amount,
                               final dev.jstech.computers.storage.IDataSink into) {
                if (done) {
                    return NetworkStorage.of(level, network).select(key, amount, into);
                }
                long moved = 0L;
                final long pooled = pool.getOrDefault(key, 0L);
                if (pooled > 0) {
                    final long want = Math.min(amount, pooled);
                    final long inserted = into.insert(key, want, false);
                    if (inserted > 0) {
                        pool.merge(key, -inserted, Long::sum);
                        moved += inserted;
                    }
                }
                if (moved < amount && lockedServers.containsKey(key)) {
                    final Map<NodeUuid, Long> byServer = NetworkStorage.of(level, network)
                            .selectBreakdown(key, amount - moved, into, lockedServers.get(key));
                    for (final Map.Entry<NodeUuid, Long> e : byServer.entrySet()) {
                        index.release(operationId, key, e.getKey(), e.getValue());
                        moved += e.getValue();
                    }
                }
                return moved;
            }

            @Override
            public long insert(final StorageKey key, final long amount) {
                if (done) {
                    return NetworkStorage.of(level, network).insert(key, amount);
                }
                pool.merge(key, amount, Long::sum);
                return amount;
            }
        };
    }

    private boolean allRunsDone() {
        for (int i = 0; i < plan.steps().size(); i++) {
            if (runsDone[i] < plan.steps().get(i).runs()) {
                return false;
            }
        }
        return true;
    }

    /** Whether the machine step at plan index {@code step} has a processing operation running right now. */
    private boolean isRunningStep(final int step) {
        for (final MachineRun run : machineRuns) {
            if (run.step == step) {
                return true;
            }
        }
        return false;
    }

    /** Lets go of the exclusively claimed computer, or the cluster's craft slot, while the machine steps work. */
    private void parkExecutors() {
        if (executorsParked) {
            return;
        }
        if (exclusiveClaim) {
            for (final CraftingComputerBlockEntity cc : executors) {
                if (cc != null) {
                    cc.releaseCraft(operationId);
                }
            }
        } else if (orchestrator != null) {
            orchestrator.releaseCraftSlot(operationId);
            orchestrator = null;
        }
        executorsParked = true;
    }

    private boolean tryLockIngredients() {
        lockedServers.clear();
        boolean covered = true;
        for (final Map.Entry<StorageKey, Long> entry : plan.rawConsumption().entrySet()) {
            final Allocation allocation = index.lock(operationId, entry.getKey(), entry.getValue());
            lockedServers.put(entry.getKey(), new java.util.HashSet<>(allocation.perServer().keySet()));
            if (!allocation.covers(entry.getValue())) {
                covered = false;
                break;
            }
        }
        if (!covered) {
            index.unlock(operationId);
            return false;
        }
        locked = true;
        return true;
    }

    private boolean tryClaimExecutors() {
        final CraftingPattern root = plan.steps().isEmpty()
                ? null : plan.steps().get(plan.steps().size() - 1).pattern();
        final var sc = findRunningSupercomputer();
        if (sc != null) {
            /*
             * Fan out: take every capable computer (fastest first) the supercomputer's free slots allow, one
             * slot per computer. The summed throughput crafts the request faster, weighted toward the faster
             * computers; requesting one slot per capable computer honors "use the maximum available computers".
             */
            final List<CraftingComputerBlockEntity> capable = capableComputers(root);
            if (capable.isEmpty()) {
                return false;
            }
            final int granted = sc.acquireCraftSlots(operationId, capable.size());
            if (granted <= 0) {
                return false; // the parallel budget is spent, wait in line
            }
            /*
             * A re-claim can flip a craft that started exclusive (no cluster then) into fan-out: reset the
             * exclusive latch so a later machine-step park releases the cluster slot, not a no-op computer claim.
             */
            orchestrator = sc;
            exclusiveClaim = false;
            executors.clear();
            executors.addAll(capable.subList(0, Math.min(granted, capable.size())));
            return true;
        }
        // No supercomputer: a single computer, claimed exclusively.
        final CraftingComputerBlockEntity cc = findCapableComputer(root, true);
        if (cc != null) {
            exclusiveClaim = true;
            orchestrator = null;
            executors.clear();
            executors.add(cc);
            return true;
        }
        return false;
    }

    @org.jetbrains.annotations.Nullable
    private dev.jstech.computers.blockentity.HbwInterfaceBlockEntity
            findRunningSupercomputer() {
        final List<dev.jstech.computers.blockentity.HbwInterfaceBlockEntity> online =
                new java.util.ArrayList<>();
        for (final BlockPos pos : supercomputers) {
            if (level.getBlockEntity(pos)
                    instanceof dev.jstech.computers.blockentity.HbwInterfaceBlockEntity sc
                    && sc.clusterOnline()) {
                online.add(sc);
            }
        }
        return chooseLeastLoaded(online);
    }

    /**
     * The supercomputer a craft should ask for slots: the online one with the most room. Every
     * supercomputer runs its own queue, so a craft must never sit waiting on a full one while another
     * has free slots, since taking the first online one did exactly that, and a second supercomputer on the
     * network never received work. When none has room, the first is returned so the craft waits in
     * line there and its next re-claim lands wherever slots free up first.
     */
    @org.jetbrains.annotations.Nullable
    public static dev.jstech.computers.blockentity.HbwInterfaceBlockEntity chooseLeastLoaded(
            final List<dev.jstech.computers.blockentity.HbwInterfaceBlockEntity> online) {
        dev.jstech.computers.blockentity.HbwInterfaceBlockEntity best = null;
        long bestFree = -1;
        for (final dev.jstech.computers.blockentity.HbwInterfaceBlockEntity sc : online) {
            final long free = sc.parallelCrafts() - sc.craftSlotsInUse();
            if (free > bestFree) {
                best = sc;
                bestFree = free;
            }
        }
        return best;
    }

    /** Who asked for this craft, as shown in the queues that list it. */
    public String requesterLabel() {
        return requesterLabel;
    }

    /** Whether this craft may run on the supercomputer whose interface sits at {@code hub}. */
    public boolean usesSupercomputer(final BlockPos hub) {
        return supercomputers.contains(hub);
    }

    /**
     * The capable computers (online, holding the root pattern), sorted fastest-first by crafting throughput.
     * Smart selection picks from the front; the supercomputer fan-out takes as many as its free slots allow.
     */
    private List<CraftingComputerBlockEntity> capableComputers(
            @org.jetbrains.annotations.Nullable final CraftingPattern root) {
        final List<CraftingComputerBlockEntity> capable = new ArrayList<>();
        for (final BlockPos pos : candidateComputers) {
            /*
             * An embedded pattern travels with the request (a multi-stage's bench stage), so knowing
             * it does not require a Recipe ROM entry of its own.
             */
            if (level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc
                    && cc.canCraft()
                    && (root == null || cc.romContains(root) || root.equals(embeddedPattern))) {
                capable.add(cc);
            }
        }
        capable.sort((a, b) -> Long.compare(b.craftingThroughput(), a.craftingThroughput()));
        return capable;
    }

    @org.jetbrains.annotations.Nullable
    private CraftingComputerBlockEntity findCapableComputer(final CraftingPattern root,
                                                            final boolean claimExclusive) {
        for (final CraftingComputerBlockEntity cc : capableComputers(root)) {
            // For an exclusive claim, fall through to the next-fastest if the fastest is already busy.
            if (!claimExclusive || cc.tryClaimCraft(operationId)) {
                return cc;
            }
        }
        return null;
    }

    /** Sum of the crafting throughput of every live executor; faster computers contribute more of each tick. */
    private long aliveThroughput() {
        long sum = 0;
        for (final CraftingComputerBlockEntity cc : executors) {
            if (cc != null && !cc.isRemoved() && cc.canCraft()) {
                sum += cc.craftingThroughput();
            }
        }
        return sum;
    }

    /**
     * Sum of the crafting-thread ceilings of the live executors: how many of this craft's machine stages may run
     * at once. The executing computer orchestrates its own stages up to this hardware ceiling, independent of the
     * Mainframe's operation queues; a supercomputer fan-out sums the thread counts of the computers it granted.
     */
    private int aliveThreads() {
        int sum = 0;
        for (final CraftingComputerBlockEntity cc : executors) {
            if (cc != null && !cc.isRemoved() && cc.canCraft()) {
                sum += cc.craftingThreads();
            }
        }
        return sum;
    }

    private boolean executorsAlive() {
        for (final CraftingComputerBlockEntity cc : executors) {
            if (cc != null && !cc.isRemoved() && cc.canCraft()) {
                return true;
            }
        }
        return false;
    }

    /** How much of {@code key} sits on the servers this operation locked, which is what {@link #consumeIngredients} can extract. */
    private long lockedServersHold(final NetworkStorage storage, final StorageKey key) {
        final java.util.Set<NodeUuid> allowed = lockedServers.get(key);
        if (allowed == null || allowed.isEmpty()) {
            return 0L;
        }
        long held = 0L;
        for (final Map.Entry<NodeUuid, Long> entry : storage.breakdown(key).entrySet()) {
            if (allowed.contains(entry.getKey())) {
                held += entry.getValue();
            }
        }
        return held;
    }

    private long consumeIngredients(final NetworkStorage storage, final CraftingPattern pattern,
                                    final long runs) {
        long executable = runs;
        /*
         * First pass: how many runs can the pools + LOCKED network actually deliver? Only ingredients this
         * operation holds a lock on may come from the network; an UNLOCKED ingredient is an intermediate that
         * must come from the pool (its upstream step), so it is never pulled from the shared network under
         * another operation's reservation, and counting it here would let this craft bypass the lock system.
         */
        for (final Map.Entry<StorageKey, Long> entry : pattern.ingredientTotals().entrySet()) {
            final long perRun = entry.getValue();
            final long pooled = pool.getOrDefault(entry.getKey(), 0L);
            /*
             * Count only the servers this operation locked (the same servers the extract below pulls from)
             * not the whole network. A concurrent machine step can drain unlocked (or another op's) servers, so
             * counting the whole network would let this craft credit runs whose input it cannot actually remove.
             */
            final long networkHas = lockedServersHold(storage, entry.getKey());
            executable = Math.min(executable, (pooled + networkHas) / perRun);
        }
        if (executable <= 0) {
            return 0;
        }
        for (final Map.Entry<StorageKey, Long> entry : pattern.ingredientTotals().entrySet()) {
            final StorageKey key = entry.getKey();
            long need = entry.getValue() * executable;
            final long fromPool = Math.min(need, pool.getOrDefault(key, 0L));
            if (fromPool > 0) {
                pool.merge(key, -fromPool, Long::sum);
                need -= fromPool;
            }
            if (need > 0) {
                if (!lockedServers.containsKey(key)) {
                    /*
                     * Intermediate shortfall with no lock: stall rather than bypass the lock by pulling
                     * from the open network; the upstream step replenishes the pool on a later tick.
                     */
                    return 0;
                }
                /*
                 * Crafting consumes the items: extract from the SAME servers the lock holds (not just any
                 * server in discovery order) so each release frees its matching reservation as the items
                 * leave, instead of missing and leaving them reserved until the craft finishes.
                 */
                final Map<NodeUuid, Long> moved = storage.selectBreakdown(
                        key, need, (k, amount, simulate) -> amount, lockedServers.get(key));
                moved.forEach((server, amount) -> index.release(operationId, key, server, amount));
            }
        }
        return executable;
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        waiting = false;
        index.unlock(operationId);
        if (orchestrator != null) {
            orchestrator.releaseCraftSlot(operationId);
        }
        if (exclusiveClaim) {
            for (final CraftingComputerBlockEntity cc : executors) {
                if (cc != null) {
                    cc.releaseCraft(operationId);
                }
            }
        }

        /*
         * Deliver the result, then return every leftover intermediate. A machine step delivered its output into
         * this craft's pool (like a bench step), so both the result and any leftover intermediates sit in the
         * pool here and go back to the network from there, so nothing is ever wasted.
         */
        deliveredResult = Math.min(pool.getOrDefault(resultKey, 0L), requested);
        if (deliveredResult > 0) {
            pool.merge(resultKey, -deliveredResult, Long::sum);
            writeBack(resultKey, deliveredResult);
        }
        for (final Map.Entry<StorageKey, Long> leftover : new LinkedHashMap<>(pool).entrySet()) {
            if (leftover.getValue() > 0) {
                writeBack(leftover.getKey(), leftover.getValue());
            }
        }
        pool.clear();

        status = deliveredResult >= requested ? OperationRecord.STATUS_COMPLETED
                : cancelled ? OperationRecord.STATUS_DISCARDED
                : timedOut ? OperationRecord.STATUS_RESOURCE_LOCKED
                : deliveredResult > 0 ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED;
        if (onSettle != null) {
            onSettle.run();
        }
    }

    @Override
    public void cancel() {
        if (done) {
            return;
        }
        cancelled = true;
        /*
         * The machine steps in flight are this craft's own stages: stop them with it. Whatever the machines
         * already hold stays in the machines, in the world, where the player can collect it.
         */
        for (final MachineRun run : new ArrayList<>(machineRuns)) {
            run.op.cancel();
        }
        finish();
    }

    private void writeBack(final StorageKey key, final long amount) {
        final ItemStack prototype = key.stack(1);
        final NetworkStorage storage = NetworkStorage.of(level, network);
        if (prototype.isEmpty()) {
            storage.insert(key, amount); // a fluid or chemical made by a machine step goes back as data
            return;
        }
        long remaining = amount;
        while (remaining > 0) {
            final int chunk = (int) Math.min(remaining, prototype.getMaxStackSize());
            final int accepted = storage.insert(prototype.copyWithCount(chunk));
            remaining -= chunk;
            final int overflow = chunk - accepted;
            final CraftingComputerBlockEntity drop = executors.isEmpty() ? null : executors.get(0);
            if (overflow > 0 && drop != null) {
                // Network storage filled mid-craft: surface the items in the world, never void them.
                net.minecraft.world.Containers.dropItemStack(level,
                        drop.getBlockPos().getX() + 0.5, drop.getBlockPos().getY() + 1.0,
                        drop.getBlockPos().getZ() + 0.5, prototype.copyWithCount(overflow));
            }
        }
    }

    @Override
    public boolean isWaiting() {
        /*
         * While its machine steps run, the craft is parked on those operations and holds no Mainframe queue of its
         * own: crafting is a subnet independent of the Mainframe's operation queues, so a parked craft frees the
         * queue for other operations while its machines (gated by the Crafting Computer's threads) do the work.
         * Its own liveRecord still reads PROCESSING (the waiting field, not this), so the log shows it under way.
         */
        return (waiting || !machineRuns.isEmpty()) && !done;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void abandon() {
        finish();
    }

    @Override
    public UUID operationId() {
        return operationId;
    }

    @Override
    public String typeId() {
        return dev.jstech.computers.operation.ComputingOperations.CRAFT;
    }

    public byte craftStatus() {
        return status;
    }

    public long delivered() {
        return deliveredResult;
    }

    /** How many crafting computers this craft is fanned out across (set when it claims; persists after it settles). */
    public int executorCount() {
        return executors.size();
    }

    public NetworkCraftOperation onSettle(final Runnable callback) {
        this.onSettle = callback;
        if (done && callback != null) {
            callback.run();
        }
        return this;
    }

    @Override
    public OperationRecord toRecord() {
        /*
         * Keep the sub-operations (each plan step) in the settled record too, so the operations log shows what a
         * craft was made of (its machine and bench stages) instead of the stages appearing as separate entries.
         */
        return buildRecord(status, true);
    }

    @Override
    public OperationRecord liveRecord() {
        final byte liveStatus = done ? status
                : waiting ? OperationRecord.STATUS_WAITING : OperationRecord.STATUS_PROCESSING;
        return buildRecord(liveStatus, true);
    }

    private OperationRecord buildRecord(final byte recordStatus, final boolean includeSubs) {
        final long produced = done ? deliveredResult : producedSoFar();
        final List<OperationRecord.MoveRow> moves = new ArrayList<>();
        if (produced > 0) {
            moves.add(new OperationRecord.MoveRow(executorLabel(), produced, requesterLabel));
        }
        final List<OperationRecord.SubRow> subs = includeSubs ? subRows() : List.of();
        return new OperationRecord(operationId, OperationRecord.TYPE_CRAFT, resultKey, requested, produced,
                recordStatus, priority, List.copyOf(moves), subs);
    }

    @Override
    public OperationPriority priority() {
        return priority;
    }

    @Override
    public void setPriority(final OperationPriority priority) {
        this.priority = java.util.Objects.requireNonNull(priority, "priority");
        // The machine steps this craft is driving are its stages: they report the same level.
        for (final MachineRun run : machineRuns) {
            run.op.setPriority(priority);
        }
    }

    private long producedSoFar() {
        if (plan.steps().isEmpty()) {
            return 0;
        }
        final int last = plan.steps().size() - 1;
        return runsDone[last] * plan.steps().get(last).perRun();
    }

    private List<OperationRecord.SubRow> subRows() {
        final List<OperationRecord.SubRow> subs = new ArrayList<>(Math.min(plan.steps().size(),
                OperationRecord.MAX_SUBS));
        for (int i = 0; i < plan.steps().size() && subs.size() < OperationRecord.MAX_SUBS; i++) {
            final CraftPlanner.Step step = plan.steps().get(i);
            /*
             * A step is streaming when it is actually being worked now: a machine step with a running operation,
             * or a bench step that has started and is not currently yielding to the machine steps.
             */
            final byte state = runsDone[i] >= step.runs() ? OperationRecord.SubRow.SUB_COMPLETED
                    : isRunningStep(i) || (!step.isMachine() && !waiting && machineRuns.isEmpty() && runsDone[i] > 0)
                        ? OperationRecord.SubRow.SUB_STREAMING
                    : OperationRecord.SubRow.SUB_READING;
            subs.add(new OperationRecord.SubRow(
                    step.resultName() + " x" + step.produced(),
                    step.runs(), runsDone[i], state));
        }
        return subs;
    }

    private String executorLabel() {
        if (executors.isEmpty()) {
            return "CC";
        }
        final String name = executors.get(0).customName();
        final String base = name.isEmpty() ? "CC" : name;
        // Show the lead computer plus how many others share the craft, so the fan-out is visible in the log.
        return executors.size() > 1 ? base + " +" + (executors.size() - 1) : base;
    }

}
