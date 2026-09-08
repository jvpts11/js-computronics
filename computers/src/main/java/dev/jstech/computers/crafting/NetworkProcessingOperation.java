/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.crafting;

import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.CraftingSwitchBlockEntity;
import dev.jstech.computers.operation.IPersistentOperation;
import dev.jstech.computers.operation.payload.OperationRecord;
import dev.jstech.computers.storage.CompositeDataPort;
import dev.jstech.computers.storage.IDataPort;
import dev.jstech.computers.storage.ExternalDataPort;
import dev.jstech.computers.storage.FilteredDataPort;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.core.operation.OperationPriority;
import dev.jstech.core.uuid.NetworkUuid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Runs a {@link ProcessingPattern} through a real machine: it finds the machine a Crafting Switch declared for
 * the pattern's machine type, feeds it one lot of inputs at a time from the network, lets it process on its own,
 * pulls the declared outputs back into the network, and counts the REAL yield (a probabilistic output's chance
 * only guided the plan, never the runtime). If nothing progresses within the pattern's timeout, it settles
 * partial/failed. Items and fluids feed in the same way; collecting a fluid back into the network is a follow-up.
 */
public final class NetworkProcessingOperation implements IPersistentOperation {

    private static final int FEED_INTERVAL = 4;
    public static final String KIND = "processing";

    private final ServerLevel level;
    private final NetworkUuid network;
    private final ProcessingPattern pattern;
    private final long requested;
    private final List<BlockPos> candidateComputers;
    private final UUID operationId;
    private final String requesterLabel;
    private final StorageKey resultKey;

    private CraftingSwitchBlockEntity.DeclaredMachine machine;
    /*
     * The physical machine the dispatcher assigned this job, so two concurrent jobs of one machine type never
     * land on the same block and jam it. Null means unassigned (a standalone run that just takes the first it can).
     */
    @Nullable
    private BlockPos assignedMachinePos;
    private long produced;
    private long lotsFed;
    /** Per pattern input: how much has reached the machine so far, against {@code lotsFed} lots' worth. */
    private long[] delivered;
    private boolean done;
    private boolean waiting = true;
    private int idleTicks;
    private int feedCooldown;
    private byte status = OperationRecord.STATUS_FAILED;
    private OperationPriority priority = OperationPriority.DEFAULT;
    private Runnable onSettle;
    private boolean concurrencyBlocked;
    /*
     * Where inputs are drawn from and outputs returned to: the network by default; a craft's isolated pool for a
     * machine step run inside a recursive craft, so concurrent steps pipeline without racing on network stock.
     */
    private final ICraftIo io;
    private final boolean ephemeral;
    /*
     * A machine step run inside a craft (given a pool ICraftIo) is "nested": it is one stage of the parent craft,
     * so it is NOT logged as an operation of its own; the parent's log entry carries it as a sub-operation.
     */
    private final boolean nested;

    public NetworkProcessingOperation(final ServerLevel level, final NetworkUuid network,
                                      final ProcessingPattern pattern, final long requested,
                                      final List<BlockPos> candidateComputers, final UUID operationId,
                                      final String requesterLabel) {
        this(level, network, pattern, requested, candidateComputers, operationId, requesterLabel, null);
    }

    public NetworkProcessingOperation(final ServerLevel level, final NetworkUuid network,
                                      final ProcessingPattern pattern, final long requested,
                                      final List<BlockPos> candidateComputers, final UUID operationId,
                                      final String requesterLabel,
                                      @org.jetbrains.annotations.Nullable final ICraftIo io) {
        this.level = level;
        this.network = network;
        this.pattern = pattern;
        this.requested = requested;
        this.candidateComputers = List.copyOf(candidateComputers);
        this.operationId = operationId;
        this.requesterLabel = requesterLabel;
        this.io = io != null ? io : ICraftIo.network(level, network);
        /*
         * A craft's machine step reads and writes that craft's isolated pool through {@code io}, but it still
         * persists across a reload: on resume it is rebuilt with the network as its I/O and finishes whatever the
         * machine still holds into the network, where the re-planned parent craft counts it as stock. So nothing
         * fed into a machine before a save is ever lost.
         */
        this.ephemeral = false;
        // A step given its own I/O is a craft's internal stage: don't log it as a separate operation.
        this.nested = io != null;
        final ProcessingPattern.ProcessingOutput primary = pattern.primaryOutput();
        this.resultKey = primary == null ? null : primary.key();
        this.delivered = new long[pattern.inputs().size()];
        if (this.resultKey == null || pattern.inputs().isEmpty() || requested <= 0) {
            finish(); // malformed pattern: settle immediately as FAILED
        }
    }

    @Override
    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        if (machine == null) {
            machine = findMachine();
            if (machine == null) {
                waiting = true;
                if (++idleTicks > pattern.timeoutTicks()) {
                    finishTimedOut();
                }
                return;
            }
            waiting = false;
        }
        /*
         * Sided machines route through crafting buses when present: an Input Bus aimed at the machine carries
         * the deliveries, a Receiving Bus the pickups. Without buses both ride the switch-touched face.
         */
        final IDataPort inPort = portFor(dev.jstech.computers.block.part.CablePartType.INPUT);
        final IDataPort outPort = portFor(dev.jstech.computers.block.part.CablePartType.RECEIVING);
        if (inPort.isEmpty() && outPort.isEmpty()) {
            machine = null; // the machine was broken/removed; re-resolve next tick
            return;
        }
        boolean progressed = false;

        /*
         * 1) Collect any finished output the machine holds, back into the sink (the network, or the craft's
         *    pool for a craft-internal step), counting the primary yield.
         */
        for (final ProcessingPattern.ProcessingOutput out : pattern.outputs()) {
            final long inMachine = outPort.count(out.key());
            if (inMachine <= 0) {
                continue;
            }
            final long pulled = outPort.extract(out.key(), inMachine, false);
            if (pulled > 0) {
                final long stored = writeBack(out.key(), pulled);
                if (out.key().equals(resultKey)) {
                    produced += stored;
                }
                progressed = true;
            }
        }
        if (produced >= requested) {
            status = OperationRecord.STATUS_COMPLETED;
            finish();
            return;
        }

        final CraftingComputerBlockEntity.MachineConfig config = resolveConfig();
        if (config.locked() || concurrencyBlocked) {
            /*
             * Paused from the Machines tab, or over the machine's concurrent-job cap: keep collecting finished
             * output, but don't feed or time out.
             */
            waiting = true;
            return;
        }
        waiting = false;

        /*
         * 2) Feed inputs when the cooldown elapses (so we don't overfill a slow machine). feedMax keeps feeding
         * until the machine is full each cycle; otherwise a single lot goes in. Feeding is bounded by the
         * demand: lots still inside the machine are expected to yield their share, so nothing beyond what the
         * request needs leaves the network. A lot whose chance-based output fell short is simply fed again.
         * A lot is only ever committed whole: what the machine could not take at once (a small chemical tank,
         * a full slot) stays owed and is topped up on the following cycles as the machine consumes.
         * What leaves the network for the machine in one tick is bounded by the Mainframe's orchestration
         * capacity, like any other transfer: a faster CPU feeds machines faster. The budget is items per tick;
         * fluids and chemicals count by the same weight (1 000 mB = one item).
         */
        if (--feedCooldown <= 0) {
            feedCooldown = FEED_INTERVAL;
            final int maxLots = config.feedMax() ? 64 : 1;
            long budgetLeft = Math.max(0L, throughputBudget) * StorageKey.MB_EQ_PER_ITEM;
            for (int lot = 0; lot < maxLots && budgetLeft > 0L; lot++) {
                if (fullyDelivered()) {
                    if (lotsFed >= lotsNeeded()) {
                        break;
                    }
                    lotsFed++;
                }
                final long movedWeight = deliverOwed(inPort, budgetLeft);
                if (movedWeight <= 0) {
                    break; // the machine is full, the network is drained, or the budget is spent
                }
                budgetLeft -= movedWeight;
                progressed = true;
                if (!fullyDelivered()) {
                    break; // the machine could not take the whole lot yet; finish it before the next one
                }
            }
        }

        if (progressed) {
            idleTicks = 0;
        } else if (++idleTicks > pattern.timeoutTicks()) {
            finishTimedOut();
        }
    }

    /**
     * Whether a declared machine serves the pattern's machine id: a generic id ({@code generic:<recipeType>})
     * matches any face the player tagged with that category; a concrete id matches by block id or face name.
     */
    public static boolean machineMatches(final CraftingSwitchBlockEntity.DeclaredMachine m, final String want) {
        if (MachineCategory.isGenericId(want)) {
            return !m.category().isEmpty() && m.category().equals(MachineCategory.categoryOf(want));
        }
        return m.machineType().equals(want) || m.name().equalsIgnoreCase(want);
    }

    /**
     * This physical machine's per-machine state (Paused / Feed), set on the Machines tab, resolved by the
     * machine's position, so pausing one machine of a type does not pause the others. (The Max Jobs ceiling is a
     * per-type setting, read by the Mainframe's dispatcher, not here.)
     */
    private CraftingComputerBlockEntity.MachineConfig resolveConfig() {
        final BlockPos machinePos = machine != null ? machine.machinePos() : assignedMachinePos;
        final String machineKey = machinePos != null
                ? CraftingComputerBlockEntity.machineStateKey(machinePos) : null;
        for (final BlockPos pos : candidateComputers) {
            if (level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc) {
                /*
                 * This one machine's own state wins; otherwise a state set on the whole machine type applies
                 * (so you can pause a single machine, or a whole type, whichever you set).
                 */
                if (machineKey != null) {
                    final CraftingComputerBlockEntity.MachineConfig perMachine = cc.machineConfig(machineKey);
                    if (perMachine != CraftingComputerBlockEntity.MachineConfig.DEFAULT) {
                        return perMachine;
                    }
                }
                final CraftingComputerBlockEntity.MachineConfig byType = cc.machineConfig(pattern.machineType());
                if (byType != CraftingComputerBlockEntity.MachineConfig.DEFAULT) {
                    return byType;
                }
            }
        }
        return CraftingComputerBlockEntity.MachineConfig.DEFAULT;
    }

    @Nullable
    private CraftingSwitchBlockEntity.DeclaredMachine findMachine() {
        CraftingSwitchBlockEntity.DeclaredMachine firstOfType = null;
        CraftingSwitchBlockEntity.DeclaredMachine firstRoutable = null;
        CraftingSwitchBlockEntity.DeclaredMachine assigned = null;
        for (final BlockPos pos : candidateComputers) {
            if (level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc) {
                for (final CraftingSwitchBlockEntity.DeclaredMachine m : cc.availableMachines()) {
                    if (!machineMatches(m, pattern.machineType())) {
                        continue;
                    }
                    if (firstOfType == null) {
                        firstOfType = m;
                    }
                    /*
                     * The dispatcher gives each concurrent job a distinct physical machine so two never share and
                     * jam one; honor that assignment exactly (it may be a fallback machine with no matching bus).
                     */
                    if (assignedMachinePos != null && assignedMachinePos.equals(m.machinePos())) {
                        assigned = m;
                    }
                    /*
                     * Prefer a machine whose Input Buses can actually route this recipe's inputs. Several
                     * machines of one type are told apart only by their bus filters (one factory filtered to
                     * iron, another to enriched iron), so picking the first by type alone feeds a machine that
                     * cannot accept the inputs. This is what makes a group of same-type machines usable.
                     */
                    if (firstRoutable == null && machineCanRoute(m)) {
                        firstRoutable = m;
                    }
                }
            }
        }
        if (assignedMachinePos != null) {
            return assigned; // null only if the assigned machine vanished; the dispatcher re-assigns next tick
        }
        // Unassigned (a standalone run): the first routable machine, or the first of the type as a last resort.
        return firstRoutable != null ? firstRoutable : firstOfType;
    }

    /**
     * The distinct physical machines this job could run on. Machines whose Input Buses can actually route its
     * inputs come first; if none can (a bus-less machine fed through the switch face, or a chemical input no item
     * bus filters), every machine of the type is a fallback, the same reach the single-machine path always had.
     * The dispatcher picks a free one per job, so concurrency scales with the machines actually present.
     */
    public List<BlockPos> routableMachines() {
        final List<BlockPos> routable = new java.util.ArrayList<>();
        final List<BlockPos> ofType = new java.util.ArrayList<>();
        for (final BlockPos pos : candidateComputers) {
            if (level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc) {
                for (final CraftingSwitchBlockEntity.DeclaredMachine m : cc.availableMachines()) {
                    if (!machineMatches(m, pattern.machineType())) {
                        continue;
                    }
                    if (!ofType.contains(m.machinePos())) {
                        ofType.add(m.machinePos());
                    }
                    if (machineCanRoute(m) && !routable.contains(m.machinePos())) {
                        routable.add(m.machinePos());
                    }
                }
            }
        }
        return routable.isEmpty() ? ofType : routable;
    }

    /** The physical machine the dispatcher assigned this job, or null if unassigned. */
    @Nullable
    public BlockPos assignedMachine() {
        return assignedMachinePos;
    }

    /**
     * The Crafting Computer that drives this job, the one whose Crafting Switch declares the machine it is
     * assigned to (or currently on). Its crafting card sets the feed rate, so the card, not the machine, governs
     * how fast the step runs. Null when no computer declares the machine (then it cannot be fed).
     */
    @Nullable
    public BlockPos executorComputer() {
        final BlockPos target = assignedMachinePos != null ? assignedMachinePos
                : (machine != null ? machine.machinePos() : null);
        if (target == null) {
            return null;
        }
        for (final BlockPos pos : candidateComputers) {
            if (level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc) {
                for (final CraftingSwitchBlockEntity.DeclaredMachine m : cc.availableMachines()) {
                    if (m.machinePos().equals(target)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** Set by the dispatcher each tick: the distinct physical machine this job may use (null to clear). */
    public void setAssignedMachine(@Nullable final BlockPos pos) {
        this.assignedMachinePos = pos;
        if (pos != null && machine != null && !pos.equals(machine.machinePos())) {
            machine = null; // re-resolve to the newly assigned machine on the next tick
        }
    }

    /**
     * Whether {@code m}'s Crafting Input Buses can carry every one of this pattern's inputs at once: each input
     * must be assignable to a distinct Input Bus whose filter selects it, and an unfiltered bus is a wildcard
     * that carries anything. A machine with no Input Bus is fed through its switch-touched face and accepts
     * anything, exactly as before this check existed.
     */
    private boolean machineCanRoute(final CraftingSwitchBlockEntity.DeclaredMachine m) {
        final List<StorageKey> filters = new java.util.ArrayList<>();
        for (final Direction d : Direction.values()) {
            final net.minecraft.core.BlockPos cablePos = m.machinePos().relative(d);
            if (level.getBlockEntity(cablePos)
                    instanceof dev.jstech.computers.blockentity.DataCableBlockEntity cable
                    && cable.getPart(d.getOpposite())
                    instanceof dev.jstech.computers.block.part.AbstractBusPart bus
                    && bus.type() == dev.jstech.computers.block.part.CablePartType.INPUT) {
                filters.add(bus.filterKey()); // null = an unfiltered bus, a wildcard
            }
        }
        if (filters.isEmpty()) {
            return true; // no Input Bus: fed through the switch-touched face, which accepts anything
        }
        final List<StorageKey> inputs = new java.util.ArrayList<>();
        for (final ProcessingPattern.ProcessingInput in : pattern.inputs()) {
            inputs.add(in.key());
        }
        return hasFullMatching(inputs, filters);
    }

    private static boolean busServes(@Nullable final StorageKey filter, final StorageKey input) {
        return filter == null || filter.equals(input);
    }

    /** True when every input can be matched to a distinct bus (Hungarian-style augmenting-path matching). */
    private static boolean hasFullMatching(final List<StorageKey> inputs, final List<StorageKey> filters) {
        final int[] inputForBus = new int[filters.size()];
        java.util.Arrays.fill(inputForBus, -1);
        for (int i = 0; i < inputs.size(); i++) {
            if (!augment(i, inputs, filters, inputForBus, new boolean[filters.size()])) {
                return false;
            }
        }
        return true;
    }

    private static boolean augment(final int input, final List<StorageKey> inputs, final List<StorageKey> filters,
                                   final int[] inputForBus, final boolean[] visited) {
        for (int b = 0; b < filters.size(); b++) {
            if (!visited[b] && busServes(filters.get(b), inputs.get(input))) {
                visited[b] = true;
                if (inputForBus[b] == -1 || augment(inputForBus[b], inputs, filters, inputForBus, visited)) {
                    inputForBus[b] = input;
                    return true;
                }
            }
        }
        return false;
    }

    private ExternalDataPort machinePort() {
        return portOn(machine.face().getOpposite());
    }

    /** The machine's port on {@code side}, with every kind of data the machine offers there. */
    private ExternalDataPort portOn(final Direction side) {
        return ExternalDataPort.at(level, machine.machinePos(), side);
    }

    /**
     * The port to move data through for the given bus kind. Every crafting cable adjacent to the machine with an
     * Input Bus (deliveries) or Receiving Bus (pickups) mounted against it contributes its machine face, and the
     * faces act as one port, which is how sided machines whose I/O faces differ from the switch-touched face, or
     * that spread outputs over several faces, are driven. A bus carrying a filter restricts its face to that one
     * key, so a machine fed two ingredients from two sides routes each to the correct face; an unfiltered bus
     * carries anything. Without a bus, the switch-touched face serves both directions.
     */
    private IDataPort portFor(final dev.jstech.computers.block.part.CablePartType kind) {
        final List<IDataPort> faces = new java.util.ArrayList<>();
        for (final Direction d : Direction.values()) {
            final net.minecraft.core.BlockPos cablePos = machine.machinePos().relative(d);
            if (level.getBlockEntity(cablePos)
                    instanceof dev.jstech.computers.blockentity.DataCableBlockEntity cable
                    && cable.getPart(d.getOpposite())
                    instanceof dev.jstech.computers.block.part.AbstractBusPart bus
                    && bus.type() == kind) {
                final ExternalDataPort port = portOn(d);
                if (!port.isEmpty()) {
                    /*
                     * Honor the bus filter so the player can pin which face each ingredient (or output) uses:
                     * a filtered face carries only that key, an empty filter carries anything.
                     */
                    final StorageKey filter = bus.filterKey();
                    faces.add(filter == null ? port : new FilteredDataPort(port, filter));
                }
            }
        }
        return faces.isEmpty() ? machinePort() : CompositeDataPort.of(faces);
    }

    /** Whether every input of the lots committed so far has reached the machine in full. */
    private boolean fullyDelivered() {
        final List<ProcessingPattern.ProcessingInput> inputs = pattern.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            if (delivered[i] < lotsFed * inputs.get(i).amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves what the machine is owed into it, within {@code budgetWeight} (mB-equivalent) for this tick, and
     * returns the weight moved. Items are owed lot by lot. Fluids and chemicals are continuous: the machine is
     * kept topped up with as much as the whole request still needs, so a tank never starves a machine that
     * could run faster than one lot every few ticks, since the pattern's amount only sets the ratio.
     */
    private long deliverOwed(final IDataPort inPort, final long budgetWeight) {
        final List<ProcessingPattern.ProcessingInput> inputs = pattern.inputs();
        long movedWeight = 0L;
        for (int i = 0; i < inputs.size() && movedWeight < budgetWeight; i++) {
            final ProcessingPattern.ProcessingInput in = inputs.get(i);
            final long lots = in.key().isItem() ? lotsFed : Math.max(lotsFed, lotsNeeded());
            final long owed = lots * in.amount() - delivered[i];
            if (owed <= 0) {
                continue;
            }
            final long unitWeight = Math.max(1L, in.key().weight(1));
            final long affordable = Math.min(owed, (budgetWeight - movedWeight) / unitWeight);
            if (affordable <= 0) {
                break;
            }
            final long sent = io.select(in.key(), affordable, inPort);
            delivered[i] += sent;
            movedWeight += sent * unitWeight;
        }
        return movedWeight;
    }

    /**
     * How many lots the request justifies. A guaranteed primary output needs exactly {@code ceil(requested /
     * amount)} lots, no more, so nothing is wasted. A probabilistic primary output cannot be counted ahead of
     * time: crediting fed lots at their expected yield cancels the real {@code produced} out of the arithmetic
     * and the machine would stop one batch short. So while the request is still short, one more lot than has
     * been fed is always allowed (a lot that rolled low is simply replaced) and the request-complete check in
     * {@link #tick} stops the op the moment real production catches up.
     */
    private long lotsNeeded() {
        final ProcessingPattern.ProcessingOutput primary = pattern.primaryOutput();
        final long perLot = Math.max(1L, primary.amount());
        if (!primary.probabilistic()) {
            return (requested + perLot - 1) / perLot;
        }
        return produced >= requested ? lotsFed : lotsFed + 1;
    }

    /** Puts {@code amount} of a key (item OR fluid) into the network; returns how much was stored. */
    private long writeBack(final StorageKey key, final long amount) {
        return io.insert(key, amount);
    }

    private void finishTimedOut() {
        status = produced > 0 ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED;
        finish();
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        waiting = false;
        if (onSettle != null) {
            onSettle.run();
        }
    }

    public NetworkProcessingOperation onSettle(final Runnable callback) {
        this.onSettle = callback;
        if (done && callback != null) {
            callback.run();
        }
        return this;
    }

    /** The machine key this op targets, used by the Mainframe to cap concurrent jobs per machine (maxJobs). */
    public String machineKey() {
        return pattern.machineType();
    }

    /** Set by the Mainframe each tick: true when this op is over its machine's concurrent-job cap. */
    public void setConcurrencyBlocked(final boolean blocked) {
        this.concurrencyBlocked = blocked;
    }

    public long produced() {
        return produced;
    }

    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }

    /** Whether this is a craft's internal machine stage (fed from the craft's pool), not a standalone operation.
     *  Nested steps are not written to the operations log on their own; the parent craft records them as its
     *  sub-operations. */
    public boolean isNested() {
        return nested;
    }

    @Override
    public UUID operationId() {
        return operationId;
    }

    @Override
    public String typeId() {
        return dev.jstech.computers.operation.ComputingOperations.PROCESSING;
    }

    @Override
    public CompoundTag saveState(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putString(KIND_KEY, KIND);
        tag.putUUID(ID_KEY, operationId);
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        ProcessingPattern.CODEC.encodeStart(ops, pattern).result().ifPresent(t -> tag.put("Pattern", t));
        tag.putLong("Requested", requested);
        tag.putLong("Produced", produced);
        tag.putLong("LotsFed", lotsFed);
        tag.putLongArray("Delivered", delivered);
        tag.putInt("IdleTicks", idleTicks);
        tag.putString("Label", requesterLabel);
        tag.putByte(NetworkCraftOperation.PRIORITY_KEY, (byte) priority.ordinal());
        return tag;
    }

    /**
     * Rebuilds a processing operation saved by {@link #saveState}. The machine is resolved again on the first
     * tick, so inputs already delivered to it are collected as they finish; the yield counted so far carries
     * over. Returns null when the saved pattern cannot be read.
     */
    @Nullable
    public static NetworkProcessingOperation restore(final CompoundTag tag, final ServerLevel level,
                                                     final NetworkUuid network, final List<BlockPos> candidateComputers,
                                                     final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final ProcessingPattern pattern = tag.contains("Pattern")
                ? ProcessingPattern.CODEC.parse(ops, tag.get("Pattern")).result().orElse(null) : null;
        if (pattern == null) {
            return null;
        }
        final NetworkProcessingOperation op = new NetworkProcessingOperation(level, network, pattern,
                tag.getLong("Requested"), candidateComputers,
                tag.hasUUID(ID_KEY) ? tag.getUUID(ID_KEY) : UUID.randomUUID(), tag.getString("Label"));
        op.produced = tag.getLong("Produced");
        op.lotsFed = tag.getLong("LotsFed");
        final long[] savedDelivered = tag.getLongArray("Delivered");
        if (savedDelivered.length == op.delivered.length) {
            op.delivered = savedDelivered;
        }
        op.idleTicks = tag.getInt("IdleTicks");
        op.priority = NetworkCraftOperation.savedPriority(tag);
        return op;
    }

    @Override
    public OperationPriority priority() {
        return priority;
    }

    @Override
    public void setPriority(final OperationPriority priority) {
        this.priority = java.util.Objects.requireNonNull(priority, "priority");
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isWaiting() {
        return waiting && !done;
    }

    @Override
    public void abandon() {
        finish();
    }

    @Override
    public void cancel() {
        if (done) {
            return;
        }
        // A run that already made everything it was asked for keeps its COMPLETED; anything short is DISCARDED.
        if (status != OperationRecord.STATUS_COMPLETED) {
            status = OperationRecord.STATUS_DISCARDED;
        }
        finish();
    }

    @Override
    public OperationRecord toRecord() {
        return buildRecord(status);
    }

    @Override
    public OperationRecord liveRecord() {
        final byte live = done ? status
                : waiting ? OperationRecord.STATUS_WAITING : OperationRecord.STATUS_PROCESSING;
        return buildRecord(live);
    }

    private OperationRecord buildRecord(final byte recordStatus) {
        final StorageKey key = resultKey != null ? resultKey
                : (pattern.inputs().isEmpty() ? null : pattern.inputs().get(0).key());
        return new OperationRecord(operationId, OperationRecord.TYPE_CRAFT, key, requested, produced,
                recordStatus, priority, List.of(), List.of());
    }
}
