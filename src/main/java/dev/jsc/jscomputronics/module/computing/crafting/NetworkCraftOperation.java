/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.crafting;

import dev.jsc.jscomputronics.common.operation.index.Allocation;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkIndex;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.PersistentOperation;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
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
public final class NetworkCraftOperation implements PersistentOperation {

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
        if (embeddedPattern != null) {
            CraftingPattern.CODEC.encodeStart(ops, embeddedPattern).result().ifPresent(t -> tag.put("Embedded", t));
        }
        if (machineStep != null && !machineStep.isDone()) {
            // The machine step keeps running on its own after a reload; the re-planned craft waits for it so
            // its output is in stock when the plan is made, instead of being made a second time.
            tag.putUUID(MACHINE_STEP_KEY, machineStep.operationId());
        }
        // Everything this craft has drained from the network but not delivered yet: intermediates and
        // finished results alike. They are handed back to storage on resume, so nothing is lost or doubled.
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
     * Resumes a bench craft saved by {@link #saveState}: the items it held in flight go back into network
     * storage, and the remaining demand is planned again as a fresh craft (reservations and computer claims
     * live in RAM and are gone after a reload, so re-planning is the honest way to continue).
     */
    public static Restored restore(final CompoundTag tag, final MainframeBlockEntity mainframe,
                                   final ServerLevel level, final NetworkUuid network,
                                   final HolderLookup.Provider registries) {
        return restore(tag, mainframe, level, network, registries, null);
    }

    /** The id of the machine step a saved craft was waiting on, or null. */
    @org.jetbrains.annotations.Nullable
    public static UUID savedMachineStep(final CompoundTag tag) {
        return tag.hasUUID(MACHINE_STEP_KEY) ? tag.getUUID(MACHINE_STEP_KEY) : null;
    }

    /**
     * Like {@link #restore(CompoundTag, MainframeBlockEntity, ServerLevel, NetworkUuid, HolderLookup.Provider)},
     * but when the craft was waiting on {@code machineStep} (restored and still running), the items in flight
     * go back to storage now and the re-plan waits until that machine step settles, so its output counts as
     * stock. Such a craft is reported as not yet running ({@code operation} null, {@code complete} false).
     */
    public static Restored restore(final CompoundTag tag, final MainframeBlockEntity mainframe,
                                   final ServerLevel level, final NetworkUuid network,
                                   final HolderLookup.Provider registries,
                                   @org.jetbrains.annotations.Nullable final NetworkProcessingOperation machineStep) {
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
        if (machineStep != null && !machineStep.isDone()) {
            // Re-plan a tick after the step settles, once the storage index has seen what it delivered.
            machineStep.onSettle(() -> mainframe.runNextTick(
                    () -> mainframe.submitNetworkCraft(result, remaining, true, label, embedded)));
            return new Restored(null, false);
        }
        return new Restored(mainframe.submitNetworkCraft(result, remaining, true, label, embedded), false);
    }

    private static final String MACHINE_STEP_KEY = "MachineStep";
    private static final int STALL_LIMIT = 100;

    public static final int DEFAULT_WAIT_TIMEOUT_TICKS = 1200;

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
    // A pattern that travels with the request instead of living in a Recipe ROM: a multi-stage
    // pipeline's bench stage embeds its pattern, so any online computer may execute it.
    @org.jetbrains.annotations.Nullable
    private final CraftingPattern embeddedPattern;

    private final Map<StorageKey, Long> pool = new HashMap<>();
    // The servers each ingredient's reservation was placed on, so the per-tick drain targets the same
    // servers and every release frees the matching reservation instead of silently missing.
    private final Map<StorageKey, java.util.Set<NodeUuid>> lockedServers = new HashMap<>();
    private final long[] runsDone;

    // With a supercomputer, one request fans out across several CCs at once (one per granted slot); without
    // one, a single exclusively-claimed CC. The per-tick rate is the summed throughput of the live executors.
    private final List<CraftingComputerBlockEntity> executors = new ArrayList<>();
    private dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity orchestrator;
    private boolean exclusiveClaim;
    private boolean locked;
    private boolean waiting = true;
    private int waitTicks;
    private boolean timedOut;
    private int stalledTicks;
    private int stepIndex;
    private long deliveredResult;
    private boolean done;
    private byte status = OperationRecord.STATUS_FAILED;
    private Runnable onSettle;
    // The Mainframe that runs this craft's machine steps as processing operations of their own; null only for
    // plans without machine steps.
    @org.jetbrains.annotations.Nullable
    private final MainframeBlockEntity mainframe;
    // The processing operation currently running the machine step at stepIndex, if any.
    @org.jetbrains.annotations.Nullable
    private NetworkProcessingOperation machineStep;
    // Intermediates handed back to the network for the running machine step, to be taken back into the pool
    // once it settles: bench steps only ever consume from the pool or from locked raw stock.
    private final Map<StorageKey, Long> flushed = new LinkedHashMap<>();
    // True while the exclusively claimed computer is let go during a machine step (the machine, not the
    // computer, is working); the craft claims a computer again once the step settles.
    private boolean executorsParked;

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
        if (plan.steps().isEmpty()) {
            finish();
        }
    }

    @Override
    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        if (waiting) {
            // Acquire in two stages, holding nothing while blocked so waiters cannot deadlock:
            // first the full ingredient reservation, then an idle computer that can execute.
            if (++waitTicks > DEFAULT_WAIT_TIMEOUT_TICKS) {
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
        }
        if (!executorsAlive()) {
            // Every computer was broken or powered off mid-craft: settle with what was produced.
            finish();
            return;
        }
        if (machineStep != null) {
            // Waiting on a machine step: poll it whatever the budget (this tick may carry none, since a waiting
            // craft holds no queue slot); bench work resumes once a slot is granted again.
            final int outcome = tickMachineStep(plan.steps().get(stepIndex), NetworkStorage.of(level, network));
            if (outcome == 0 || done) {
                return;
            }
            if (stepIndex >= plan.steps().size()) {
                finish();
                return;
            }
            if (waiting) {
                return; // a computer is claimed again on the next tick before bench work resumes
            }
        }

        // The craft runs at the summed throughput of its computers (never above the Mainframe's grant). Work is
        // measured in ingredient items handled, so denser recipes genuinely take longer; faster computers in the
        // fan-out contribute proportionally more of each tick's work.
        final long rate = Math.min(throughputBudget, aliveThroughput());
        if (rate <= 0) {
            return;
        }
        long budget = rate;
        boolean progressed = false;
        final NetworkStorage storage = NetworkStorage.of(level, network);

        while (budget > 0 && stepIndex < plan.steps().size()) {
            final CraftPlanner.Step step = plan.steps().get(stepIndex);
            final long remainingRuns = step.runs() - runsDone[stepIndex];
            if (remainingRuns <= 0) {
                stepIndex++;
                continue;
            }
            if (step.isMachine()) {
                // A machine step runs as a processing operation of its own; this craft waits on it and pulls
                // its output into the pool for the steps after it. Waiting is progress, not a stall.
                final int outcome = tickMachineStep(step, storage);
                if (outcome > 0) {
                    progressed = true;
                    continue;
                }
                progressed |= outcome == 0;
                break;
            }
            final long unitsPerRun = step.unitsPerRun();
            final long runsAffordable = Math.max(budget >= unitsPerRun ? budget / unitsPerRun : 0, 0);
            if (runsAffordable <= 0) {
                break; // budget exhausted mid-step; resume next tick
            }
            final long runsNow = Math.min(remainingRuns, runsAffordable);
            final long executable = consumeIngredients(storage, step.pattern(), runsNow);
            if (executable <= 0) {
                break; // ingredients not deliverable this tick (stall counter decides)
            }
            pool.merge(StorageKey.of(step.pattern().result()),
                    executable * step.pattern().result().getCount(), Long::sum);
            runsDone[stepIndex] += executable;
            budget -= executable * unitsPerRun;
            progressed = true;
            if (runsDone[stepIndex] >= step.runs()) {
                stepIndex++;
            }
        }

        if (stepIndex >= plan.steps().size()) {
            finish();
        } else if (!progressed && ++stalledTicks >= STALL_LIMIT) {
            finish();
        } else if (progressed) {
            stalledTicks = 0;
        }
    }

    /**
     * Drives the machine step at {@code stepIndex}: starts its processing operation on the first call (after
     * handing this craft's intermediates back to the network, where the machine draws its inputs from), then
     * waits for it. Returns 1 once the step is complete and its output sits in the pool, 0 while the machine
     * is still working, and -1 when the step could not start or fell short.
     */
    private int tickMachineStep(final CraftPlanner.Step step, final NetworkStorage storage) {
        if (machineStep == null) {
            if (mainframe == null) {
                finish();
                return -1;
            }
            flushIntermediates();
            machineStep = mainframe.submitNetworkProcessing(step.machine(), step.produced(), requesterLabel);
            if (machineStep == null) {
                finish();
                return -1;
            }
            // Neither the computer nor the cluster's craft slots do anything while the machine works: free them
            // for other crafts and claim again once the step settles (the ingredients stay locked).
            if (exclusiveClaim) {
                for (final CraftingComputerBlockEntity cc : executors) {
                    cc.releaseCraft(operationId);
                }
                executorsParked = true;
            } else if (orchestrator != null) {
                orchestrator.releaseCraftSlot(operationId);
                orchestrator = null;
                executorsParked = true;
            }
            return 0;
        }
        if (!machineStep.isDone()) {
            return 0;
        }
        if (executorsParked) {
            // Back to the acquisition phase for a computer (the ingredients stay locked).
            executorsParked = false;
            waiting = true;
            waitTicks = 0;
        }
        // Take back what was handed to the network for this step, then the step's own output.
        for (final Map.Entry<StorageKey, Long> entry : flushed.entrySet()) {
            final long back = storage.select(entry.getKey(), entry.getValue(), (key, amount, simulate) -> amount);
            if (back > 0) {
                pool.merge(entry.getKey(), back, Long::sum);
            }
        }
        flushed.clear();
        final StorageKey made = step.resultKey();
        final long wanted = Math.min(step.produced(), machineStep.produced());
        final long got = made == null || wanted <= 0 ? 0
                : storage.select(made, wanted, (key, amount, simulate) -> amount);
        if (got > 0) {
            pool.merge(made, got, Long::sum);
        }
        runsDone[stepIndex] = Math.min(step.runs(), got / step.perRun());
        final boolean complete = got >= step.produced();
        machineStep = null;
        stepIndex++;
        return complete ? 1 : -1;
    }

    /**
     * Hands every intermediate (never the result itself) back to the network before a machine step — the
     * machine draws its inputs from storage — remembering the amounts so they return to the pool afterwards.
     */
    private void flushIntermediates() {
        for (final Map.Entry<StorageKey, Long> entry : new LinkedHashMap<>(pool).entrySet()) {
            if (entry.getValue() > 0 && !entry.getKey().equals(resultKey)) {
                writeBack(entry.getKey(), entry.getValue());
                flushed.merge(entry.getKey(), entry.getValue(), Long::sum);
                pool.remove(entry.getKey());
            }
        }
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
            // Fan out: take every capable computer (fastest first) the supercomputer's free slots allow — one
            // slot per computer. The summed throughput crafts the request faster, weighted toward the faster
            // computers; requesting one slot per capable computer honors "use the maximum available computers".
            final List<CraftingComputerBlockEntity> capable = capableComputers(root);
            if (capable.isEmpty()) {
                return false;
            }
            final int granted = sc.acquireCraftSlots(operationId, capable.size());
            if (granted <= 0) {
                return false; // the parallel budget is spent — wait in line
            }
            // A re-claim can flip a craft that started exclusive (no cluster then) into fan-out: reset the
            // exclusive latch so a later machine-step park releases the cluster slot, not a no-op computer claim.
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
    private dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity
            findRunningSupercomputer() {
        for (final BlockPos pos : supercomputers) {
            if (level.getBlockEntity(pos)
                    instanceof dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity sc
                    && sc.clusterOnline()) {
                return sc;
            }
        }
        return null;
    }

    /**
     * The capable computers (online, holding the root pattern), sorted fastest-first by crafting throughput.
     * Smart selection picks from the front; the supercomputer fan-out takes as many as its free slots allow.
     */
    private List<CraftingComputerBlockEntity> capableComputers(
            @org.jetbrains.annotations.Nullable final CraftingPattern root) {
        final List<CraftingComputerBlockEntity> capable = new ArrayList<>();
        for (final BlockPos pos : candidateComputers) {
            // An embedded pattern travels with the request (a multi-stage's bench stage), so knowing
            // it does not require a Recipe ROM entry of its own.
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

    private boolean executorsAlive() {
        for (final CraftingComputerBlockEntity cc : executors) {
            if (cc != null && !cc.isRemoved() && cc.canCraft()) {
                return true;
            }
        }
        return false;
    }

    /** How much of {@code key} sits on the servers this operation locked — what {@link #consumeIngredients} can extract. */
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
        // First pass: how many runs can the pools + LOCKED network actually deliver? Only ingredients this
        // operation holds a lock on may come from the network; an UNLOCKED ingredient is an intermediate that
        // must come from the pool (its upstream step), so it is never pulled from the shared network under
        // another operation's reservation — counting it here would let this craft bypass the lock system.
        for (final Map.Entry<StorageKey, Long> entry : pattern.ingredientTotals().entrySet()) {
            final long perRun = entry.getValue();
            final long pooled = pool.getOrDefault(entry.getKey(), 0L);
            // Count only the servers this operation locked — the same servers the extract below pulls from —
            // not the whole network. A concurrent machine step can drain unlocked (or another op's) servers, so
            // counting the whole network would let this craft credit runs whose input it cannot actually remove.
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
                    // Intermediate shortfall with no lock: stall rather than bypass the lock by pulling
                    // from the open network; the upstream step replenishes the pool on a later tick.
                    return 0;
                }
                // Crafting consumes the items: extract from the SAME servers the lock holds (not just any
                // server in discovery order) so each release frees its matching reservation as the items
                // leave, instead of missing and leaving them reserved until the craft finishes.
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

        // Deliver the result, then return every leftover intermediate — nothing is ever wasted.
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

        status = timedOut ? OperationRecord.STATUS_RESOURCE_LOCKED
                : deliveredResult >= requested ? OperationRecord.STATUS_COMPLETED
                : deliveredResult > 0 ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED;
        if (onSettle != null) {
            onSettle.run();
        }
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
        // Blocked on a machine step, the craft is waiting on that operation: it holds no queue slot of its own,
        // otherwise a Mainframe with a single queue could never tick the machine it waits for.
        return (waiting || machineStep != null) && !done;
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
        return buildRecord(status, false);
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
        return new OperationRecord(OperationRecord.TYPE_CRAFT, resultKey, requested, produced,
                recordStatus, List.copyOf(moves), subs);
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
            final byte state = runsDone[i] >= step.runs() ? OperationRecord.SubRow.SUB_COMPLETED
                    : i == stepIndex && !waiting ? OperationRecord.SubRow.SUB_STREAMING
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
