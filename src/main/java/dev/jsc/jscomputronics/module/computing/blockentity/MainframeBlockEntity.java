/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.common.hardware.FormFactor;
import dev.jsc.jscomputronics.common.network.ConnectivityIndex;
import dev.jsc.jscomputronics.common.network.DataNetworkConnectable;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.network.FailoverRole;
import dev.jsc.jscomputronics.common.network.MainframeNode;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.operation.OperationDispatch;
import dev.jsc.jscomputronics.common.operation.OperationPriority;
import dev.jsc.jscomputronics.common.operation.OperationTask;
import dev.jsc.jscomputronics.common.operation.SelfTestOperationTask;
import dev.jsc.jscomputronics.common.persistence.NetworkRegistrySavedData;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NetworkUuidState;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframeStructure;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * The Mainframe BlockEntity: the binding that turns installed hardware item stacks into a {@link ComputerBuild} and exposes the powered state, capacity and parallel-queue count. Unlike the passive computers it shares a base with, the Mainframe OWNS and orchestrates a data network rather than reading one from a cable.
 */
public class MainframeBlockEntity extends AbstractComputerBlockEntity
        implements dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost {

    @Override
    public java.util.Set<Long> occupiedPositions(final long ownerPos) {
        // The whole 3x2x2 footprint is one connection surface: a peripheral cable
        final Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final java.util.Set<Long> positions = new java.util.HashSet<>();
        for (final BlockPos p : MainframeStructure.allPositions(worldPosition, facing)) {
            positions.add(p.asLong());
        }
        return positions;
    }

    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOTS_START = 1;
    public static final int CPU_SLOTS = 4;
    public static final int RAM_SLOTS_START = 5;
    public static final int RAM_SLOTS = 8;
    public static final int GPU_SLOTS_START = 13;
    public static final int GPU_SLOTS = 6;
    public static final int PSU_SLOT = 19;
    public static final int DISK_SLOTS_START = 20;
    public static final int DISK_SLOTS = 4;
    public static final int TOTAL_SLOTS = 24;

    private static final ComputerHardwareLayout LAYOUT = new ComputerHardwareLayout(
            MOTHERBOARD_SLOT, CPU_SLOTS_START, CPU_SLOTS, RAM_SLOTS_START, RAM_SLOTS,
            GPU_SLOTS_START, GPU_SLOTS, PSU_SLOT, DISK_SLOTS_START, DISK_SLOTS, TOTAL_SLOTS);

    public static final int STORAGE_SLOTS = 27;

    private boolean networkConflict;
    private boolean failoverEnabled;
    private FailoverRole failoverRole = FailoverRole.NONE;
    private int failoverWaitTicks;
    @Nullable
    private NetworkUuid nativeNetworkUuid;

    @Nullable
    private OperationDispatch dispatch;
    private int dispatchQueues;
    private final dev.jsc.jscomputronics.module.computing.operation.NetworkIndex networkIndex =
            new dev.jsc.jscomputronics.module.computing.operation.NetworkIndex();
    private final java.util.List<dev.jsc.jscomputronics.module.computing.operation.NetworkOperation>
            activeOperations = new java.util.ArrayList<>();
    private long completedTotal;
    // Operations that were in flight when the world was saved, waiting for the first booted tick to resume.
    @Nullable
    private net.minecraft.nbt.ListTag pendingOperations;
    private int resumeCountdown;
    // Ticks to let the storage index and the switch surveys settle after a boot before resuming operations.
    private static final int RESUME_DELAY_TICKS = 20;
    // Set when the block is being destroyed, so setRemoved can tell a break (discard) from a chunk unload (keep).
    private boolean broken;

    // The IQL Engine: a service installed on the Mainframe that holds the network's saved IQL objects
    // (views/procedures/jobs) and runs the jobs. The catalog persists with the Mainframe; the NMS only
    // opens when the Engine is installed and running. A running Mainframe runs its Engine by default.
    private final dev.jsc.jscomputronics.module.computing.program.iql.IqlCatalog iqlCatalog =
            new dev.jsc.jscomputronics.module.computing.program.iql.IqlCatalog();
    private boolean iqlEngineInstalled;
    private boolean iqlEngineRunning = true;
    private final dev.jsc.jscomputronics.module.computing.program.IqlJobAgent iqlJobAgent =
            new dev.jsc.jscomputronics.module.computing.program.IqlJobAgent();
    /** Jobs the player paused from the Processes tab (lowercased names); a paused job never fires. */
    private final java.util.Set<String> pausedJobs = new java.util.HashSet<>();
    /** The last script the NMS editor held, persisted so it survives closing and reopening the studio. */
    private String savedScript = "";

    private static final int OPERATION_LOG_MAX = 32;
    private static final int FAILOVER_PROMOTE_DELAY = 60;
    private final java.util.Deque<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord>
            operationLog = new java.util.ArrayDeque<>();

    public MainframeBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.MAINFRAME_BE.get(), pos, state, LAYOUT);
    }

    @Override
    protected Set<FormFactor> acceptedFormFactors() {
        // A Mainframe takes the MTX board of every shipped era.
        return Set.of(FormFactor.MTX);
    }

    /**
     * A Mainframe accepts only an MTX board of its own era: a board of a different era is neither
     * installable nor counted in the build.
     */
    @Override
    protected dev.jsc.jscomputronics.common.tier.HardwareEra requiredBoardEra() {
        return blockEra();
    }

    private dev.jsc.jscomputronics.common.tier.HardwareEra blockEra() {
        return getBlockState().getBlock()
                instanceof dev.jsc.jscomputronics.module.computing.block.MainframeBlock mf
                ? mf.era()
                : dev.jsc.jscomputronics.common.tier.HardwareEra.STANDARD;
    }

    // Slot validity is governed by the inherited instance isValidForSlot, which the acceptedFormFactors
    // override above ties to MTX-only boards over the CPU/RAM/PCIe/disk ranges of the shared layout.

    // The hardware handler is inherited; this shim keeps the historic public name so the Menu/Screen,
    // block drops and GameTests address it unchanged.
    public ItemStackHandler getInventory() {
        return getHardware();
    }

    @Override
    protected String hardwareNbtKey() {
        // Preserve the historic key so every existing saved Mainframe keeps its installed hardware.
        return "Inventory";
    }

    public dev.jsc.jscomputronics.module.computing.storage.LocalStore localStore() {
        final java.util.List<ItemStack> disks = new java.util.ArrayList<>(DISK_SLOTS);
        for (int i = 0; i < DISK_SLOTS; i++) {
            disks.add(getHardware().getStackInSlot(DISK_SLOTS_START + i));
        }
        return new dev.jsc.jscomputronics.module.computing.storage.LocalStore(disks, this::setChanged);
    }

    /**
     * The net storage capacity in item-equivalents after subtracting the installed OS footprint.
     * This is the capacity available for data held on the network and in the local store; the OS
     * occupies disk space from installation.
     */
    public long storageItems() {
        final ComputerBuild build = currentBuild();
        if (build == null) {
            return 0L;
        }
        return Math.max(0L, build.totalStorageItems() - reservedByOs());
    }

    public int parallelQueues() {
        return buildValid() ? currentBuild().parallelQueues() : 0;
    }

    // Network connection

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final MainframeBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        nativeNetworkUuid(); // the mainframe owns a network identity from placement on
        if (!isRunning()) {
            leaveNetwork(level);
            closeDispatch();
            return;
        }
        updateNetwork(level);
        if (networkConflict) {
            // A contested network collapses: discard every in-flight Operation (its progress is
            closeDispatch();
            return;
        }
        if (failoverRole == FailoverRole.PASSIVE) {
            // A Passive standby holds no dispatcher and runs no Operations until it is promoted; the
            // Active member owns the network. closeDispatch settles anything left from a demotion.
            closeDispatch();
            return;
        }
        if (!hasOs()) {
            // Network orchestration requires a booted OS. Without one the Mainframe holds its network
            // UUID and topology but skips the dispatcher, index, Operation processing, and the IQL job
            // agent. Any submitted Operations remain PENDING until an OS is installed; on the tick when
            // the OS becomes present the dispatcher picks them up automatically (self-healing).
            return;
        }
        runDispatch();
        // Reconcile the in-RAM storage catalog with the network's servers: a changes-only ANALYZE
        networkIndex.analyzeIncremental(level, networkUuid());
        restorePendingOperations(level);
        tickOperations();
        // The IQL Engine's job agent fires scheduled/conditional jobs (no-op unless the Engine runs).
        iqlJobAgent.tick(this, level);
    }

    public dev.jsc.jscomputronics.module.computing.operation.NetworkIndex networkIndex() {
        return networkIndex;
    }

    // Network OWNERSHIP — the Mainframe orchestrates its own network instead of reading one from a
    // single cable, so the base's passive registerNode/unregisterNode/tickNode path is unused here.

    @Override
    protected void registerNode(final NetworkSystem system, final NetworkUuid network) {
        // No-op: the Mainframe owns and orchestrates its network through updateNetwork/orchestrate
        // rather than registering as a passive member; the base's tickNode is never invoked for it.
    }

    @Override
    protected void unregisterNode(final NetworkSystem system, final NetworkUuid network) {
        // No-op: ownership teardown runs through unregister(NetworkSystem)/onBroken(), not this hook.
    }

    private void updateNetwork(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final ConnectivityIndex index = system.connectivity();
        final java.util.Set<Long> cables = adjacentCables(level);
        // Bridge every cable run this Mainframe touches into one segment, so the topology connected
        // through the Mainframe is a single network.
        index.bridge(cables);

        // The network already laid on a touched cable, if any — a primary's network to join, or an
        NetworkUuid adopted = null;
        for (final long cable : cables) {
            final Optional<NetworkUuid> segment = index.networkOf(cable);
            if (segment.isPresent()) {
                adopted = segment.get();
                break;
            }
        }

        final java.util.List<MainframeBlockEntity> peers = otherRunningMainframesOnSegment(level, index, cables);
        final boolean primaryPeerPresent = peers.stream().anyMatch(peer -> !peer.failoverEnabled);

        if (!failoverEnabled) {
            failoverRole = FailoverRole.NONE;
            failoverWaitTicks = 0;
            final NetworkUuid effective = adopted != null ? adopted : nativeNetworkUuid();
            if (adopted == null) {
                NetworkRegistrySavedData.get(level).addNetwork(effective);
            }
            setConflict(level, primaryPeerPresent);
            if (primaryPeerPresent) {
                // Two primaries on one network: collapse it until they are physically separated.
                NetworkRegistrySavedData.get(level).setNetworkState(effective, NetworkUuidState.CONFLICTED);
                networkUuid = null;
                unregister(system);
                return;
            }
            orchestrate(level, system, index, cables, effective);
            return;
        }

        setConflict(level, false); // a standby never holds the network in conflict on its own
        if (adopted == null) {
            // Not on any network yet: dormant until it reaches a primary's network.
            failoverRole = FailoverRole.PASSIVE;
            failoverWaitTicks = 0;
            networkUuid = null;
            unregister(system);
            return;
        }
        if (primaryPeerPresent) {
            // The primary owns and orchestrates this network; the standby merely stands by on it.
            failoverRole = FailoverRole.PASSIVE;
            failoverWaitTicks = 0;
            networkUuid = adopted;
            unregister(system);
            return;
        }
        // No primary present — it is gone and the network is orphaned. The lowest-positioned standby
        // takes that SAME network over after the takeover delay; the rest keep standing by.
        final boolean superiorStandbyPresent = peers.stream()
                .anyMatch(peer -> peer.worldPosition.asLong() < worldPosition.asLong());
        updateFailoverRole(superiorStandbyPresent);
        if (failoverRole == FailoverRole.PASSIVE) {
            networkUuid = adopted;
            unregister(system);
            return;
        }
        orchestrate(level, system, index, cables, adopted);
    }

    private void orchestrate(final ServerLevel level, final NetworkSystem system, final ConnectivityIndex index,
                             final java.util.Set<Long> cables, final NetworkUuid effective) {
        // A cable whose BlockEntity has not registered yet (mid chunk-load) is skipped, picked up later.
        for (final long cable : cables) {
            if (index.contains(cable) && !effective.equals(index.networkOf(cable).orElse(null))) {
                index.assignUuid(cable, effective);
            }
        }
        networkUuid = effective;
        // Restore the network from any prior CONFLICTED or ORPHANED state — adopting it revives it.
        NetworkRegistrySavedData.get(level).setNetworkState(effective, NetworkUuidState.ACTIVE);
        system.registerMainframe(snapshot(effective));
        system.recordMainframePosition(effective, worldPosition.asLong());
        registeredNetwork = effective;
    }

    private void updateFailoverRole(final boolean superiorPresent) {
        if (superiorPresent) {
            failoverRole = FailoverRole.PASSIVE; // a preferred Active is running — stand by
            failoverWaitTicks = 0;
        } else if (failoverRole == FailoverRole.PASSIVE) {
            // The Active this member was backing is gone; take over after the promotion delay.
            if (++failoverWaitTicks >= FAILOVER_PROMOTE_DELAY) {
                failoverRole = FailoverRole.ACTIVE;
                failoverWaitTicks = 0;
            }
        } else {
            // Lowest-positioned and not standing by — own the network immediately (initial election).
            failoverRole = FailoverRole.ACTIVE;
            failoverWaitTicks = 0;
        }
    }

    public boolean submitOperation(final OperationTask task, final OperationPriority priority) {
        if (dispatch == null || !isRunning()) {
            return false;
        }
        dispatch.submit(task, priority);
        return true;
    }

    private void leaveNetwork(final ServerLevel level) {
        networkUuid = null;
        setConflict(level, false);
        unregister(NetworkSystem.get(level));
    }

    private void unregister(final NetworkSystem system) {
        if (registeredNetwork != null) {
            system.unregisterMainframe(registeredNetwork, nodeUuid());
            registeredNetwork = null;
        }
    }

    public void onBroken() {
        broken = true;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final NetworkSystem system = NetworkSystem.get(serverLevel);
        // The LAST Mainframe out orphans the network — whatever its failover role. While another
        final boolean survivorPresent = !otherRunningMainframesOnSegment(
                serverLevel, system.connectivity(), adjacentCables(serverLevel)).isEmpty();
        if (!survivorPresent) {
            orphanOwnedNetwork(serverLevel);
        }
        unregister(system);
    }

    private void orphanOwnedNetwork(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final ConnectivityIndex index = system.connectivity();
        final java.util.Set<NetworkUuid> owned = new java.util.LinkedHashSet<>();
        if (registeredNetwork != null) {
            owned.add(registeredNetwork);
        }
        for (final long cable : adjacentCables(level)) {
            index.networkOf(cable).ifPresent(owned::add);
        }
        final NetworkRegistrySavedData registry = NetworkRegistrySavedData.get(level);
        for (final NetworkUuid net : owned) {
            registry.setNetworkState(net, NetworkUuidState.ORPHANED);
        }
    }

    private void eraseOwnedNetwork(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final ConnectivityIndex index = system.connectivity();
        final NetworkRegistrySavedData registry = NetworkRegistrySavedData.get(level);
        for (final NetworkUuid net : new java.util.LinkedHashSet<>(java.util.Arrays.asList(networkUuid, registeredNetwork))) {
            if (net != null) {
                index.clearNetwork(net);
                registry.removeNetwork(net);
            }
        }
        networkUuid = null;
        unregister(system);
    }

    private java.util.List<MainframeBlockEntity> otherRunningMainframesOnSegment(
            final ServerLevel level, final ConnectivityIndex index, final java.util.Set<Long> cables) {
        final java.util.Map<Long, MainframeBlockEntity> found = new java.util.LinkedHashMap<>();
        final java.util.Set<Long> scanned = new java.util.HashSet<>();
        for (final long anchor : cables) {
            for (final long cablePos : index.componentPositions(anchor)) {
                if (!scanned.add(cablePos)) {
                    continue;
                }
                final BlockPos base = BlockPos.of(cablePos);
                for (final Direction direction : Direction.values()) {
                    final MainframeBlockEntity mainframe = mainframeBehind(level, base.relative(direction));
                    if (mainframe != null && mainframe != this && mainframe.isRunning()) {
                        found.putIfAbsent(mainframe.worldPosition.asLong(), mainframe);
                    }
                }
            }
        }
        return new java.util.ArrayList<>(found.values());
    }

    @Nullable
    private MainframeBlockEntity mainframeBehind(final ServerLevel level, final BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MainframeBlockEntity mainframe) {
            return mainframe;
        }
        if (be instanceof MainframePartBlockEntity part && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof MainframeBlockEntity controller) {
            return controller;
        }
        return null;
    }

    private void setConflict(final ServerLevel level, final boolean conflict) {
        if (conflict != networkConflict && level.getServer() != null) {
            final String message = conflict
                    ? "[J's Computronics] NETWORK_CONFLICT: two Mainframes share one network near "
                            + worldPosition.toShortString()
                    : "[J's Computronics] Network conflict resolved near " + worldPosition.toShortString();
            level.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal(message), false);
        }
        networkConflict = conflict;
    }

    // The Mainframe is a 3x2x2 multiblock, so it scans for a cable across its whole footprint and
    // bridges every touched cable into one network — replacing the base's single-cable scan.
    private java.util.Set<Long> adjacentCables(final ServerLevel level) {
        final Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final java.util.Set<Long> inside = new java.util.HashSet<>();
        for (final BlockPos p : MainframeStructure.allPositions(worldPosition, facing)) {
            if (p.equals(worldPosition)) {
                inside.add(p.asLong());
            } else if (level.getBlockEntity(p) instanceof MainframePartBlockEntity part
                    && worldPosition.equals(part.controllerPos())) {
                inside.add(p.asLong());
            }
        }
        // Collect EVERY cable on an external face, not just the first — the mainframe
        // bridges all of them into its single network.
        final java.util.Set<Long> cables = new java.util.LinkedHashSet<>();
        for (final long posLong : inside) {
            final BlockPos p = BlockPos.of(posLong);
            for (final Direction direction : Direction.values()) {
                final BlockPos neighbor = p.relative(direction);
                if (inside.contains(neighbor.asLong())) {
                    continue; // a face internal to the multiblock
                }
                if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock cable
                        && acceptsTier(cable.tier())) {
                    cables.add(neighbor.asLong());
                }
            }
        }
        return cables;
    }

    @Override
    protected boolean acceptsTier(final DataTier tier) {
        return getBlockState().getBlock() instanceof DataNetworkConnectable device
                && device.acceptedCableTiers().contains(tier);
    }

    private MainframeNode snapshot(final NetworkUuid network) {
        return new MainframeNode(nodeUuid(), network, capacity(),
                FailoverRole.NONE, Optional.empty(), 0L);
    }

    // Operation dispatch (the virtual-thread runtime)

    private void runDispatch() {
        final int queues = Math.max(1, parallelQueues());
        if (dispatch == null) {
            dispatch = new OperationDispatch(queues);
            dispatchQueues = queues;
        } else if (dispatchQueues != queues) {
            // The GPU count changed (a hot-swap): resize the dispatcher's lanes in place. Rebuilding it would run
            // closeDispatch, which abandons every in-flight Operation — so pulling a GPU mid-craft would discard
            // the craft and leave its machines stranded. Resizing keeps the active Operations running untouched.
            dispatch.setParallelQueues(queues);
            dispatchQueues = queues;
        }
        dispatch.tick();
    }

    /**
     * The virtual-thread dispatcher, created on demand. A timed Operation submitted between ticks (from
     * a terminal, the CLI or a bus) parks each disk's read latency on it; the per-tick reconciliation in
     * {@link #runDispatch()} still owns recreating it when the parallel-queue count changes.
     */
    private OperationDispatch ensureDispatch() {
        if (dispatch == null) {
            final int queues = Math.max(1, parallelQueues());
            dispatch = new OperationDispatch(queues);
            dispatchQueues = queues;
        }
        return dispatch;
    }

    private void closeDispatch() {
        closeDispatch(false);
    }

    /**
     * Tears the dispatcher down. With {@code keepPersistent}, the resumable Operations are dropped WITHOUT
     * being abandoned or logged: this is the chunk-unload path, where the block entity was just written to
     * disk with those Operations inside it, and abandoning them here would move items after the save (a
     * craft returning its pool) — items the resumed Operation would move again on reload.
     */
    private void closeDispatch(final boolean keepPersistent) {
        if (dispatch != null) {
            // Fold the dying dispatcher's tally into the persisted lifetime total so the
            // completed count carries across power cycles and chunk unloads.
            completedTotal += dispatch.completedCount();
            dispatch.close();
            dispatch = null;
            dispatchQueues = 0;
            // Settle every in-flight multi-tick Operation first, so a holder polling isDone() (an
            // INSERT returning leftover, a SELECT freeing its lock) recovers; then record each as
            // DISCARDED so a conflict or power-off leaves a trace in the log instead of vanishing.
            for (final var operation : activeOperations) {
                if (keepPersistent && operation instanceof dev.jsc.jscomputronics.module.computing.operation
                        .PersistentOperation persistent && !persistent.isEphemeral()) {
                    continue; // already saved with the block entity; it resumes on reload
                }
                operation.abandon();
                recordOperation(operation.toRecord().withStatus(
                        dev.jsc.jscomputronics.module.computing.operation.payload
                                .OperationRecord.STATUS_DISCARDED));
            }
            activeOperations.clear();
            // The index lives in RAM: powering off clears the catalog, rebuilt on the next start.
            networkIndex.clear();
        }
    }

    public int submitSelfTest(final int count, final int workUnits) {
        if (dispatch == null || !isRunning()) {
            return 0;
        }
        final SelfTestOperationTask task = new SelfTestOperationTask(workUnits);
        for (int i = 0; i < count; i++) {
            dispatch.submit(task, OperationPriority.MEDIUM);
        }
        return count;
    }

    public int pendingOps() {
        final int slots = Math.max(1, parallelQueues());
        int used = 0;
        int queued = 0;
        for (final var operation : activeOperations) {
            if (operation.isDone()) {
                continue;
            }
            if (operation.isWaiting()) {
                queued++; // blocked on another Operation's LOCK — not streaming
            } else if (!occupiesQueue(operation)) {
                continue; // a machine stage runs under its computer's threads, never queued in the Mainframe
            } else if (used < slots) {
                used++;
            } else {
                queued++;
            }
        }
        return queued + (dispatch == null ? 0 : dispatch.pendingCount());
    }

    public int runningOps() {
        final int slots = Math.max(1, parallelQueues());
        int used = 0;
        for (final var operation : activeOperations) {
            if (!operation.isDone() && !operation.isWaiting() && occupiesQueue(operation) && used < slots) {
                used++;
            }
        }
        return used + (dispatch == null ? 0 : dispatch.runningCount());
    }

    public long completedOps() {
        return completedTotal + (dispatch == null ? 0L : dispatch.completedCount());
    }

    public void recordOperation(final byte type, final ItemStack icon, final long requested,
                                final long moved, final byte status,
                                final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.MoveRow> moves) {
        recordOperation(new dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord(
                type, dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(icon),
                requested, moved, status, java.util.List.copyOf(moves)));
    }

    public void recordOperation(
            final dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord record) {
        operationLog.addFirst(record);
        while (operationLog.size() > OPERATION_LOG_MAX) {
            operationLog.removeLast();
        }
        setChanged();
    }

    // Multi-tick network Operations (decomposed into SubOperations)

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkSelect(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final dev.jsc.jscomputronics.module.computing.storage.DataSink destination, final String destinationLabel) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_SELECT, null);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkSelect(
            final net.minecraft.world.item.Item item, final long demand,
            final dev.jsc.jscomputronics.module.computing.storage.DataSink destination, final String destinationLabel) {
        return submitNetworkSelect(dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item),
                demand, destination, destinationLabel);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkSelect(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final dev.jsc.jscomputronics.module.computing.storage.DataSink destination, final String destinationLabel,
            final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_SELECT, sources);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkSelect(
            final net.minecraft.world.item.Item item, final long demand,
            final dev.jsc.jscomputronics.module.computing.storage.DataSink destination, final String destinationLabel,
            final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        return submitNetworkSelect(dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item),
                demand, destination, destinationLabel, sources);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkMove(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final dev.jsc.jscomputronics.module.computing.storage.DataSink destination, final String destinationLabel,
            final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_MOVE, sources);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkDelete(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final dev.jsc.jscomputronics.module.computing.storage.DataSink destination, final String destinationLabel) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_DELETE, null);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkDelete(
            final net.minecraft.world.item.Item item, final long demand,
            final dev.jsc.jscomputronics.module.computing.storage.DataSink destination, final String destinationLabel) {
        return submitNetworkDelete(dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item),
                demand, destination, destinationLabel);
    }

    @Nullable
    private dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitPull(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final dev.jsc.jscomputronics.module.computing.storage.DataSink destination, final String destinationLabel,
            final byte recordType,
            final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        // A SELECT/MOVE/DELETE also needs the dispatcher; without an OS the Operation would never tick and would
        // just pile up in activeOperations. Refuse it so callers no-op cleanly instead of accumulating dead work.
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return null;
        }
        final var operation = new dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation(
                serverLevel, networkUuid(), key, demand, destination, destinationLabel, recordType,
                java.util.UUID.randomUUID(), networkIndex, ensureDispatch(), sources);
        activeOperations.add(operation);
        return operation;
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation submitNetworkInsert(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final String sourceLabel) {
        // Without a booted OS the dispatcher never ticks (see tick()), so an Operation submitted here would
        // sit forever in activeOperations holding items the caller already took out of the world. Refuse it so
        // callers hit their op == null branch and return the items to the player instead of losing them.
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return null;
        }
        final var operation = new dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation(
                serverLevel, networkUuid(), key, demand, sourceLabel, networkIndex, ensureDispatch());
        activeOperations.add(operation);
        return operation;
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation submitNetworkInsert(
            final net.minecraft.world.item.Item item, final long demand, final String sourceLabel) {
        return submitNetworkInsert(dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item),
                demand, sourceLabel);
    }

    // CRAFT — recursive autocrafting over the network's Crafting Computers

    public java.util.List<net.minecraft.core.BlockPos> craftingComputerPositions() {
        if (networkUuid() == null || !(level instanceof ServerLevel serverLevel)) {
            return java.util.List.of();
        }
        final java.util.List<net.minecraft.core.BlockPos> positions = new java.util.ArrayList<>();
        for (final var node : dev.jsc.jscomputronics.common.network.NetworkSystem.get(serverLevel)
                .craftingComputersOf(networkUuid())) {
            positions.add(net.minecraft.core.BlockPos.of(node.pos()));
        }
        return positions;
    }

    public java.util.List<net.minecraft.core.BlockPos> supercomputerPositions() {
        if (networkUuid() == null || !(level instanceof ServerLevel serverLevel)) {
            return java.util.List.of();
        }
        final java.util.List<net.minecraft.core.BlockPos> positions = new java.util.ArrayList<>();
        for (final var node : dev.jsc.jscomputronics.common.network.NetworkSystem.get(serverLevel)
                .supercomputersOf(networkUuid())) {
            positions.add(net.minecraft.core.BlockPos.of(node.pos()));
        }
        return positions;
    }

    /**
     * The network's parallel craft-slot capacity from its online supercomputers, as {@code [used, total]}.
     * The Tasks view uses it to show how many crafts can run at once and how many are currently running.
     */
    public int[] supercomputerCraftSlots() {
        int used = 0;
        int total = 0;
        for (final net.minecraft.core.BlockPos pos : supercomputerPositions()) {
            if (level != null && level.getBlockEntity(pos)
                    instanceof dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity sc
                    && sc.clusterOnline()) {
                total += (int) sc.parallelCrafts();
                used += sc.craftSlotsInUse();
            }
        }
        return new int[] {used, total};
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern> networkPatterns() {
        final java.util.List<dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern> patterns =
                new java.util.ArrayList<>();
        for (final net.minecraft.core.BlockPos pos : craftingComputerPositions()) {
            if (level != null && level.getBlockEntity(pos)
                    instanceof CraftingComputerBlockEntity cc && cc.isRunning()) {
                patterns.addAll(cc.romPatterns());
            }
        }
        return patterns;
    }

    /** The plain machine (processing) patterns on the network, for the recursive craft planner. */
    public java.util.List<dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern> networkProcessingPatterns() {
        final java.util.List<dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern> machines =
                new java.util.ArrayList<>();
        for (final var recipe : networkMachineRecipes()) {
            recipe.proc().ifPresent(machines::add);
        }
        return machines;
    }

    /** Every machine recipe (processing / multi-stage) the network's running Crafting Computers hold. */
    public java.util.List<dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe> networkMachineRecipes() {
        final java.util.List<dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe> recipes =
                new java.util.ArrayList<>();
        for (final net.minecraft.core.BlockPos pos : craftingComputerPositions()) {
            if (level != null && level.getBlockEntity(pos)
                    instanceof CraftingComputerBlockEntity cc && cc.isRunning()) {
                recipes.addAll(cc.machineRecipes());
            }
        }
        return recipes;
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation submitNetworkCraft(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final boolean partial, final String requesterLabel) {
        return submitNetworkCraft(key, demand, partial, requesterLabel, null);
    }

    /**
     * Same as {@link #submitNetworkCraft(dev.jsc.jscomputronics.module.computing.storage.StorageKey, long,
     * boolean, String)}, but plans with one extra pattern alongside the network's Recipe ROMs. A multi-stage
     * pipeline's bench stage carries its own embedded pattern, so it must craft even when that pattern was
     * never loaded into any Recipe ROM on the network.
     */
    @Nullable
    public dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation submitNetworkCraft(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final boolean partial, final String requesterLabel,
            @Nullable final dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern extraPattern) {
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null
                || demand <= 0) {
            return null;
        }
        final var patterns = networkPatterns();
        if (extraPattern != null && !patterns.contains(extraPattern)) {
            patterns.add(extraPattern);
        }
        // Machine patterns take part in the plan: an ingredient no bench makes may come out of a machine.
        final var machines = networkProcessingPatterns();
        final var stock = networkIndex.snapshot();
        long target = demand;
        var plan = dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner.plan(
                key, target, patterns, machines, stock);
        if (plan.steps().isEmpty()) {
            return null; // no pattern on the network produces this item
        }
        if (!plan.feasible()) {
            if (!partial) {
                return null;
            }
            target = dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner.maxFeasible(
                    key, demand, patterns, machines, stock);
            if (target <= 0) {
                return null;
            }
            plan = dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner.plan(
                    key, target, patterns, machines, stock);
        }
        // The record keeps the ORIGINAL request: a scaled-down partial run settles as
        // COMPLETED_PARTIAL showing produced vs requested, exactly what the player asked to see.
        final var operation = new dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation(
                serverLevel, networkUuid(), key, demand, plan, networkIndex,
                java.util.UUID.randomUUID(), craftingComputerPositions(), supercomputerPositions(),
                requesterLabel, extraPattern, this);
        activeOperations.add(operation);
        return operation;
    }

    /**
     * Runs a machine recipe: feeds a {@link dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern}'s
     * inputs into the matching machine (declared on a Crafting Switch) and collects its outputs back into the
     * network, until {@code demand} of the primary output is produced or the pattern times out.
     */
    public dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation submitNetworkProcessing(
            final dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern pattern, final long demand,
            final String requesterLabel) {
        return submitNetworkProcessing(pattern, demand, requesterLabel, null);
    }

    /**
     * As above, but the step draws its inputs from and returns its outputs to {@code io} instead of the network.
     * A recursive craft passes its own pool here so its machine steps pipeline through the pool (concurrent,
     * race-free) rather than through the shared network; such a step is ephemeral and does not persist a reload.
     */
    public dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation submitNetworkProcessing(
            final dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern pattern, final long demand,
            final String requesterLabel,
            @Nullable final dev.jsc.jscomputronics.module.computing.crafting.CraftIo io) {
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null
                || demand <= 0) {
            return null;
        }
        final var operation = new dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation(
                serverLevel, networkUuid(), pattern, demand, craftingComputerPositions(),
                java.util.UUID.randomUUID(), requesterLabel, io);
        activeOperations.add(operation);
        return operation;
    }

    /** Runs a multi-stage recipe: an ordered pipeline of bench/processing stages, one at a time. */
    public dev.jsc.jscomputronics.module.computing.crafting.NetworkMultiStageOperation submitNetworkMultiStage(
            final dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern pattern, final long demand,
            final String requesterLabel) {
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel) || networkUuid() == null || demand <= 0) {
            return null;
        }
        final var operation = new dev.jsc.jscomputronics.module.computing.crafting.NetworkMultiStageOperation(
                this, pattern, demand, requesterLabel);
        activeOperations.add(operation);
        return operation;
    }

    /**
     * The single craft entry point every OS surface — the terminal, the Network Interactor, and the CLI/IQL —
     * routes a request through, so all three behave the same. If a machine recipe on the network produces
     * {@code key} directly, that recipe runs (a processing run, or a multi-stage pipeline); otherwise a recursive
     * bench-and-machine craft is planned. When a machine recipe's own inputs are not all in stock and other
     * patterns can make them, the whole tree runs as one craft with the machine as a step; if nothing can make a
     * missing input, the bare machine run delivers what the network does hold. {@code partial} applies only to
     * the recursive fallback (a machine run always delivers what it can). {@code onSettle}, if given, fires when
     * the resulting operation settles. Returns the operation, or null if nothing on the network makes {@code key}.
     */
    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkOperation submitCraftRequest(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final boolean partial, final String label, @Nullable final Runnable onSettle) {
        return submitCraftRequest(key, demand, partial, label, onSettle, true);
    }

    /**
     * As above, but {@code preferMultiStage} chooses which recipe wins when an item can be made BOTH by a
     * multi-stage pipeline and by composing the individual step patterns: true runs the multi-stage recipe; false
     * skips it and lets the recursive planner build the tree from the flat patterns. Only affects results that
     * have a multi-stage recipe; everything else routes the same way regardless.
     */
    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkOperation submitCraftRequest(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final boolean partial, final String label, @Nullable final Runnable onSettle,
            final boolean preferMultiStage) {
        for (final var recipe : networkMachineRecipes()) {
            if (!key.equals(recipe.resultKey())) {
                continue;
            }
            if (recipe.proc().isPresent()) {
                if (!inputsInStock(recipe.proc().get(), demand)) {
                    // A machine input is missing: if other patterns can make it, run the whole tree as one craft
                    // (all-or-nothing, so partial is false here); otherwise fall through to the bare machine run.
                    final var planned = submitNetworkCraft(key, demand, false, label);
                    if (planned != null) {
                        if (onSettle != null) {
                            planned.onSettle(onSettle);
                        }
                        return planned;
                    }
                }
                final var op = submitNetworkProcessing(recipe.proc().get(), demand, label);
                if (op != null && onSettle != null) {
                    op.onSettle(onSettle);
                }
                return op;
            }
            if (recipe.multi().isPresent() && preferMultiStage) {
                final var op = submitNetworkMultiStage(recipe.multi().get(), demand, label);
                if (op != null && onSettle != null) {
                    op.onSettle(onSettle);
                }
                return op;
            }
        }
        // No machine makes it directly (or multi-stage was declined): plan a recursive bench-and-machine craft.
        final var op = submitNetworkCraft(key, demand, partial, label);
        if (op != null && onSettle != null) {
            op.onSettle(onSettle);
        }
        return op;
    }

    /** Whether the network has a multi-stage recipe whose end result is {@code key} (so a caller can offer the
     *  player the choice between the pipeline and the flat, recursively-planned path). */
    public boolean hasMultiStageRecipe(final dev.jsc.jscomputronics.module.computing.storage.StorageKey key) {
        for (final var recipe : networkMachineRecipes()) {
            if (key.equals(recipe.resultKey()) && recipe.multi().isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the network currently stocks every input a processing run producing {@code quantity} would use. */
    private boolean inputsInStock(final dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern machine,
                                  final long quantity) {
        if (!(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return true;
        }
        final var storage = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .of(serverLevel, networkUuid());
        final var primary = machine.primaryOutput();
        final long runs = ceilDiv(quantity, primary == null ? 1 : Math.max(1, primary.amount()));
        final java.util.Map<dev.jsc.jscomputronics.module.computing.storage.StorageKey, Long> need =
                new java.util.HashMap<>();
        for (final var in : machine.inputs()) {
            need.merge(in.key(), in.amount() * runs, Long::sum);
        }
        for (final var entry : need.entrySet()) {
            if (storage.count(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static long ceilDiv(final long amount, final long perRun) {
        return (amount + perRun - 1) / perRun;
    }

    // Manual LOCK / UNLOCK — player-issued holds on a network item type that make concurrent
    // Operations WAIT, the explicit handle on storage concurrency.

    public long lockType(final dev.jsc.jscomputronics.module.computing.storage.StorageKey key,
                         final long demand,
                         @Nullable final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        if (!isRunning() || networkUuid() == null) {
            return 0L;
        }
        return networkIndex.manualLock(key, demand, sources);
    }

    public long unlockType(final dev.jsc.jscomputronics.module.computing.storage.StorageKey key) {
        return networkIndex.manualUnlock(key);
    }

    public java.util.Map<dev.jsc.jscomputronics.module.computing.storage.StorageKey, Long> lockedTypes() {
        return networkIndex.manualLockView();
    }

    /** The configured concurrent-job cap for a machine key, from the first Crafting Computer that set one. */
    private int resolveMaxJobs(final String machineKey) {
        for (final net.minecraft.core.BlockPos pos : craftingComputerPositions()) {
            if (level != null && level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc) {
                final CraftingComputerBlockEntity.MachineConfig cfg = cc.machineConfig(machineKey);
                if (cfg != CraftingComputerBlockEntity.MachineConfig.DEFAULT) {
                    return cfg.maxJobs();
                }
            }
        }
        return CraftingComputerBlockEntity.MachineConfig.DEFAULT.maxJobs();
    }

    /**
     * Rebuilds the Operations that were in flight when the world was saved, once the boot has settled (the
     * storage index is analyzed and the Crafting Switch surveys have run). Stage operations are restored
     * first so a multi-stage pipeline can find the stage it was waiting on by id.
     */
    private void restorePendingOperations(final ServerLevel level) {
        if (pendingOperations == null || networkUuid() == null) {
            return;
        }
        if (++resumeCountdown < RESUME_DELAY_TICKS) {
            return;
        }
        final net.minecraft.nbt.ListTag saved = pendingOperations;
        pendingOperations = null;
        final HolderLookup.Provider registries = level.registryAccess();
        final java.util.Map<java.util.UUID, dev.jsc.jscomputronics.module.computing.operation.NetworkOperation>
                byId = new java.util.HashMap<>();
        final java.util.Set<java.util.UUID> completedStages = new java.util.HashSet<>();
        final java.util.List<dev.jsc.jscomputronics.module.computing.crafting.NetworkMultiStageOperation>
                pipelines = new java.util.ArrayList<>();
        // Crafts that had machine steps in flight re-plan only once every operation is back, so the steps they
        // were running can be found by id and their output counted before the remaining demand is planned.
        final java.util.List<CompoundTag> craftsOnMachines = new java.util.ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            final CompoundTag tag = saved.getCompound(i);
            final java.util.UUID savedId = tag.hasUUID(
                    dev.jsc.jscomputronics.module.computing.operation.PersistentOperation.ID_KEY)
                    ? tag.getUUID(dev.jsc.jscomputronics.module.computing.operation.PersistentOperation.ID_KEY)
                    : java.util.UUID.randomUUID();
            switch (tag.getString(dev.jsc.jscomputronics.module.computing.operation.PersistentOperation.KIND_KEY)) {
                case dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation.KIND -> {
                    final var op = dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation
                            .restore(tag, level, networkUuid(), craftingComputerPositions(), registries);
                    if (op != null) {
                        activeOperations.add(op);
                        byId.put(savedId, op);
                    }
                }
                case dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation.KIND -> {
                    if (!dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation
                            .savedMachineSteps(tag).isEmpty()) {
                        craftsOnMachines.add(tag);
                        continue;
                    }
                    // submitNetworkCraft already registers the re-planned craft in activeOperations.
                    final var restored = dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation
                            .restore(tag, this, level, networkUuid(), registries);
                    if (restored.operation() != null) {
                        byId.put(savedId, restored.operation());
                    } else if (restored.complete()) {
                        completedStages.add(savedId);
                    }
                }
                case dev.jsc.jscomputronics.module.computing.crafting.NetworkMultiStageOperation.KIND -> {
                    final var op = dev.jsc.jscomputronics.module.computing.crafting.NetworkMultiStageOperation
                            .restore(tag, this, registries);
                    if (op != null) {
                        activeOperations.add(op);
                        pipelines.add(op);
                        byId.put(savedId, op);
                    }
                }
                default -> { }
            }
        }
        for (final CompoundTag tag : craftsOnMachines) {
            final java.util.List<dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation> steps =
                    new java.util.ArrayList<>();
            for (final java.util.UUID stepId : dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation
                    .savedMachineSteps(tag)) {
                if (byId.get(stepId)
                        instanceof dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation proc) {
                    steps.add(proc);
                }
            }
            dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation
                    .restore(tag, this, level, networkUuid(), registries, steps);
        }
        for (final var pipeline : pipelines) {
            final java.util.UUID stageId = pipeline.pendingStageId();
            if (stageId != null && completedStages.contains(stageId)) {
                pipeline.skipCompletedStage();
            } else {
                pipeline.adoptStage(stageId == null ? null : byId.get(stageId));
            }
        }
        setChanged();
    }

    /** Work queued by an operation's settle callback that must run on a later tick (the index is current then). */
    private final java.util.List<Runnable> deferredWork = new java.util.ArrayList<>();

    /** Runs {@code work} on the next operations tick, after the storage index has caught up with this one. */
    public void runNextTick(final Runnable work) {
        deferredWork.add(work);
    }

    private void tickOperations() {
        if (!deferredWork.isEmpty()) {
            final java.util.List<Runnable> work = new java.util.ArrayList<>(deferredWork);
            deferredWork.clear();
            work.forEach(Runnable::run);
        }
        if (activeOperations.isEmpty()) {
            return;
        }
        // Progress lives in the Operations themselves and is saved with this block entity.
        setChanged();
        // A queue processes at most the RAM buffer per tick: a buffer smaller than the CPU leaves
        // the CPU idle waiting on RAM, so the effective rate is the lesser of the two.
        final long effectiveCapacity = Math.min(capacity(), ramBuffer());
        final int slots = Math.max(1, parallelQueues());
        int used = 0;
        assignMachines();
        // A machine step feeds at its Crafting Computer's crafting-card throughput (card x CPU), NOT the
        // Mainframe's capacity — the card is what governs how fast any craft runs, bench or machine. That
        // throughput is SHARED among the steps one computer is driving at once, so a computer feeding three
        // machines splits its card's throughput three ways (the machine's own speed is still the ceiling).
        final java.util.Map<net.minecraft.core.BlockPos, Integer> stepsPerComputer = new java.util.HashMap<>();
        for (final var operation : activeOperations) {
            if (operation instanceof dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation proc
                    && !proc.isDone() && !proc.isWaiting()) {
                final net.minecraft.core.BlockPos cc = proc.executorComputer();
                if (cc != null) {
                    stepsPerComputer.merge(cc, 1, Integer::sum);
                }
            }
        }
        // Iterate a snapshot: a multi-stage operation submits its sub-stage into activeOperations mid-tick,
        // which would otherwise be a concurrent modification. The new stage simply ticks next tick.
        for (final var operation : new java.util.ArrayList<>(activeOperations)) {
            if (operation.isDone()) {
                continue;
            }
            if (operation.isWaiting()) {
                operation.tick(0L); // lock retry + timeout only; holds no queue slot
            } else if (!occupiesQueue(operation)) {
                // A craft's machine stage runs under its Crafting Computer's thread ceiling, not a Mainframe
                // queue: it always gets its feed and never counts against the queue budget.
                operation.tick(machineFeedBudget(operation, effectiveCapacity, stepsPerComputer));
            } else if (used < slots) {
                used++;
                operation.tick(machineFeedBudget(operation, effectiveCapacity, stepsPerComputer));
            }
            // Ready Operations beyond the queue count stay PENDING this tick: no progress,
            // no latency countdown — their disks have not started reading yet.
        }
        final java.util.Iterator<dev.jsc.jscomputronics.module.computing.operation.NetworkOperation> it =
                activeOperations.iterator();
        while (it.hasNext()) {
            final var operation = it.next();
            if (operation.isDone()) {
                // A craft's machine steps are nested stages, not operations of their own: the parent craft logs
                // them as its sub-operations, so don't write them to the log or the lifetime tally separately.
                final boolean nested = operation instanceof dev.jsc.jscomputronics.module.computing.crafting
                        .NetworkProcessingOperation proc && proc.isNested();
                if (!nested) {
                    final var record = operation.toRecord();
                    recordOperation(record);
                    if (record.status() == dev.jsc.jscomputronics.module.computing.operation.payload
                            .OperationRecord.STATUS_COMPLETED) {
                        completedTotal++; // network Operations count toward the lifetime tally too
                    }
                }
                it.remove();
            }
        }
    }

    /**
     * Gives every running processing job a distinct physical machine, so concurrency on a machine type scales
     * with the machines actually present — two same-type jobs never share (and jam) one block. A job keeps the
     * machine it already holds (as long as it is still there and routable); a new job claims a free one of the
     * ones its recipe can route to. A job with no free machine is flagged blocked (it waits, it does not time
     * out). The Machines tab's Max Jobs is an OPTIONAL per-type ceiling on top of this: 0 means "use them all".
     */
    private void assignMachines() {
        final java.util.Set<net.minecraft.core.BlockPos> taken = new java.util.HashSet<>();
        final java.util.Map<String, Integer> perType = new java.util.HashMap<>();
        final java.util.List<dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation> jobs =
                new java.util.ArrayList<>();
        for (final var operation : activeOperations) {
            if (operation instanceof dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation proc
                    && !proc.isDone()) {
                jobs.add(proc);
            }
        }
        // Pass 1: a job that already holds a still-valid, unclaimed machine keeps it (stable across ticks so a
        // machine is never fed by two jobs turn and turn about).
        for (final var proc : jobs) {
            final net.minecraft.core.BlockPos held = proc.assignedMachine();
            if (held != null && !taken.contains(held) && proc.routableMachines().contains(held)) {
                taken.add(held);
                perType.merge(proc.machineKey(), 1, Integer::sum);
                proc.setConcurrencyBlocked(false);
            } else {
                proc.setAssignedMachine(null);
            }
        }
        // Pass 2: an unassigned job claims a free routable machine, within its type's optional Max Jobs ceiling.
        for (final var proc : jobs) {
            if (proc.assignedMachine() != null) {
                continue;
            }
            final String key = proc.machineKey();
            final int ceiling = resolveMaxJobs(key); // 0 = auto: no ceiling, bounded only by the machines present
            if (ceiling > 0 && perType.getOrDefault(key, 0) >= ceiling) {
                proc.setConcurrencyBlocked(true);
                continue;
            }
            net.minecraft.core.BlockPos free = null;
            for (final net.minecraft.core.BlockPos candidate : proc.routableMachines()) {
                if (!taken.contains(candidate)) {
                    free = candidate;
                    break;
                }
            }
            if (free != null) {
                proc.setAssignedMachine(free);
                taken.add(free);
                perType.merge(key, 1, Integer::sum);
                proc.setConcurrencyBlocked(false);
            } else {
                proc.setConcurrencyBlocked(true); // every machine of this type is busy: wait, do not time out
            }
        }
    }

    /**
     * The per-tick throughput to run {@code operation} at. A machine step feeds at its Crafting Computer's
     * crafting-card throughput (card x CPU), shared among the steps that computer drives at once — the card, not
     * the machine, sets the crafting speed. Everything else runs at the Mainframe's own orchestration capacity.
     */
    private long machineFeedBudget(final dev.jsc.jscomputronics.module.computing.operation.NetworkOperation operation,
                                   final long effectiveCapacity,
                                   final java.util.Map<net.minecraft.core.BlockPos, Integer> stepsPerComputer) {
        if (operation instanceof dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation proc) {
            final net.minecraft.core.BlockPos cc = proc.executorComputer();
            if (cc == null) {
                return 0L; // no Crafting Computer drives this machine, so it cannot be fed
            }
            final long throughput = level != null
                    && level.getBlockEntity(cc) instanceof CraftingComputerBlockEntity computer
                    ? computer.craftingThroughput() : 0L;
            return throughput / Math.max(1, stepsPerComputer.getOrDefault(cc, 1));
        }
        return effectiveCapacity;
    }

    /**
     * Whether {@code operation} occupies one of the Mainframe's operation queues. A craft's machine stage is
     * orchestrated by its Crafting Computer and gated by that computer's crafting threads, not by the Mainframe,
     * so it never occupies a Mainframe queue: the queues gate distinct operations (a craft, a SELECT, an INSERT),
     * the crafting threads gate one craft's concurrent stages. Every non-stage operation occupies a queue.
     */
    private static boolean occupiesQueue(
            final dev.jsc.jscomputronics.module.computing.operation.NetworkOperation operation) {
        return !(operation instanceof dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation proc
                && proc.isNested());
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> recentOperations() {
        return java.util.List.copyOf(operationLog);
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> activeOperationRecords() {
        final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> out =
                new java.util.ArrayList<>(activeOperations.size());
        final int slots = Math.max(1, parallelQueues());
        int used = 0;
        for (final var operation : activeOperations) {
            var record = operation.liveRecord();
            if (!operation.isDone() && !operation.isWaiting() && occupiesQueue(operation)) {
                if (used < slots) {
                    used++;
                } else {
                    record = record.withStatus(dev.jsc.jscomputronics.module.computing.operation.payload
                            .OperationRecord.STATUS_PENDING);
                }
            }
            // A machine stage keeps its live status (PROCESSING while it runs): it is gated by its Crafting
            // Computer's threads, not a Mainframe queue, so the queue limit never forces it to PENDING.
            out.add(record);
        }
        return out;
    }

    public boolean hasActiveOperations() {
        return !activeOperations.isEmpty();
    }

    public NetworkUuid nativeNetworkUuid() {
        if (nativeNetworkUuid == null) {
            nativeNetworkUuid = NetworkUuid.random();
            setChanged();
        }
        return nativeNetworkUuid;
    }

    public boolean hasNetworkConflict() {
        return networkConflict;
    }

    public boolean failoverEnabled() {
        return failoverEnabled;
    }

    public FailoverRole failoverRole() {
        return failoverRole;
    }

    public void toggleFailover() {
        failoverEnabled = !failoverEnabled;
        if (failoverEnabled && level instanceof ServerLevel serverLevel) {
            eraseOwnedNetwork(serverLevel);
        }
        setChanged();
    }

    // ComputerTerminalHost — read-only monitoring for the Monitor terminal

    @Override
    public boolean computerRunning() {
        return isRunning();
    }

    @Override
    public boolean computerBuildValid() {
        return buildValid();
    }

    @Override
    public int networkLinkState() {
        return networkConflict ? NET_STATE_CONFLICT : (networkUuid != null ? NET_STATE_LINKED : NET_STATE_NONE);
    }

    @Override
    public long orchestrationCapacity() {
        return capacity();
    }

    @Override
    public int computerQueues() {
        return parallelQueues();
    }

    @Override
    public long computerRamBuffer() {
        return ramBuffer();
    }

    @Override
    public int networkServerCount() {
        if (networkUuid != null && level instanceof ServerLevel serverLevel) {
            return NetworkSystem.get(serverLevel).serversOf(networkUuid).size();
        }
        return 0;
    }

    @Override
    public long localStorageUsed() {
        return localStore().used();
    }

    @Override
    public long localStorageCapacity() {
        return storageItems();
    }

    @Override
    public dev.jsc.jscomputronics.module.computing.storage.DataSink localStorage() {
        return new dev.jsc.jscomputronics.module.computing.storage.StoreSink(localStore());
    }

    public java.util.Map<dev.jsc.jscomputronics.module.computing.storage.StorageKey, Long> localSnapshot() {
        return localStore().view();
    }

    @Override
    public int usableStorageSlots() {
        final long capacity = storageItems(); // already net of OS footprint
        return capacity <= 0 ? 0 : (int) Math.min(STORAGE_SLOTS, (capacity + 63) / 64);
    }

    @Override
    public int pendingOperations() {
        return pendingOps();
    }

    @Override
    public int runningOperations() {
        return runningOps();
    }

    @Override
    public int completedOperations() {
        return (int) Math.min(Integer.MAX_VALUE, completedOps());
    }

    @Override
    public int networkPcCount() {
        if (networkUuid != null && level instanceof ServerLevel serverLevel) {
            return NetworkSystem.get(serverLevel).personalComputersOf(networkUuid).size();
        }
        return 0;
    }

    @Override
    public int networkSubframeCount() {
        if (networkUuid != null && level instanceof ServerLevel serverLevel) {
            return NetworkSystem.get(serverLevel).subframesOf(networkUuid).size();
        }
        return 0;
    }

    @Override
    public boolean isMainframeHost() {
        return true;
    }

    @Override
    public int indexedTypes() {
        return networkIndex.catalogSize();
    }

    @Override
    public int indexedServers() {
        return networkIndex.indexedServerCount();
    }

    @Override
    public int activeLocks() {
        return networkIndex.activeLockCount();
    }

    @Override
    public long networkStorageUsed() {
        return networkIndex.usedWeight()
                / dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
    }

    @Override
    public long networkStorageTotal() {
        if (networkUuid == null || !(level instanceof ServerLevel serverLevel)) {
            return 0L;
        }
        return NetworkSystem.get(serverLevel).totalStorageOf(networkUuid)
                / dev.jsc.jscomputronics.common.hardware.DiskSpec.MB_PER_ITEM;
    }

    public static final int DATA_RUNNING = 0;
    public static final int DATA_BUILD_VALID = 1;
    public static final int DATA_CAPACITY = 2;
    public static final int DATA_PARALLEL_QUEUES = 3;
    public static final int DATA_RAM_BUFFER = 4;
    public static final int DATA_AUTOSTART = 5;
    public static final int DATA_MANUAL_ON = 6;
    public static final int DATA_NETWORK_STATE = 7;
    public static final int DATA_PENDING_OPS = 8;
    public static final int DATA_RUNNING_OPS = 9;
    public static final int DATA_COMPLETED_OPS = 10;
    public static final int DATA_FAILOVER_ENABLED = 11;
    public static final int DATA_FAILOVER_ROLE = 12;
    public static final int DATA_COUNT = DATA_FAILOVER_ROLE + 1;

    public static final int NET_STATE_NONE = 0;
    public static final int NET_STATE_LINKED = 1;
    public static final int NET_STATE_CONFLICT = 2;

    private final int[] clientData = new int[DATA_COUNT];

    private int computeData(final int index) {
        return switch (index) {
            case DATA_RUNNING -> isRunning() ? 1 : 0;
            case DATA_BUILD_VALID -> buildValid() ? 1 : 0;
            case DATA_CAPACITY -> (int) Math.min(Integer.MAX_VALUE, capacity());
            case DATA_PARALLEL_QUEUES -> parallelQueues();
            case DATA_RAM_BUFFER -> (int) Math.min(Integer.MAX_VALUE, ramBuffer());
            case DATA_AUTOSTART -> isAutoStart() ? 1 : 0;
            case DATA_MANUAL_ON -> isManualOn() ? 1 : 0;
            case DATA_NETWORK_STATE ->
                    networkConflict ? NET_STATE_CONFLICT : (networkUuid != null ? NET_STATE_LINKED : NET_STATE_NONE);
            case DATA_PENDING_OPS -> pendingOps();
            case DATA_RUNNING_OPS -> runningOps();
            case DATA_COMPLETED_OPS -> (int) Math.min(Integer.MAX_VALUE, completedOps());
            case DATA_FAILOVER_ENABLED -> failoverEnabled ? 1 : 0;
            case DATA_FAILOVER_ROLE -> failoverRole.ordinal();
            default -> 0;
        };
    }

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(final int index) {
            if (level != null && level.isClientSide) {
                return index >= 0 && index < clientData.length ? clientData[index] : 0;
            }
            return computeData(index);
        }

        @Override
        public void set(final int index, final int value) {
            if (index >= 0 && index < clientData.length) {
                clientData[index] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Covers both destruction and chunk-unload: stop the virtual-thread executor
        // and drop the registry snapshot, so neither leaks for a mainframe that is gone.
        // This override is authoritative — the base setRemoved's passive onBroken/unregisterNode
        // path is a no-op for the Mainframe (its node hooks are no-ops), so there is no double teardown.
        // A chunk unload keeps the resumable Operations (they are in the saved NBT); a break discards them.
        closeDispatch(!broken);
        if (level instanceof ServerLevel serverLevel) {
            unregister(NetworkSystem.get(serverLevel));
        }
    }

    @Override
    protected void loadExtra(final CompoundTag tag, final HolderLookup.Provider registries) {
        // Hardware (under the "Inventory" key), ManualOn, AutoStart, NodeUuid, LinkedMonitors and
        // Console are loaded by the base; only the Mainframe-only state is restored here.
        failoverEnabled = tag.getBoolean("Failover");
        // Persist the standby role + countdown so a reload mid-promotion does not reset the timer (which
        // could, with frequent chunk cycling, stop a standby from ever promoting).
        if (tag.contains("FailoverRole")) {
            try {
                failoverRole = FailoverRole.valueOf(tag.getString("FailoverRole"));
            } catch (final IllegalArgumentException ignored) {
                failoverRole = FailoverRole.NONE;
            }
        }
        failoverWaitTicks = tag.getInt("FailoverWaitTicks");
        if (tag.contains("NetworkUuid")) {
            nativeNetworkUuid = NetworkUuid.fromString(tag.getString("NetworkUuid"));
        }
        completedTotal = tag.getLong("CompletedTotal");
        operationLog.clear();
        final net.minecraft.nbt.ListTag ops = tag.getList("OperationLog", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < ops.size() && i < OPERATION_LOG_MAX; i++) {
            operationLog.addLast(dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord
                    .fromNbt(ops.getCompound(i), registries));
        }
        if (tag.contains("ActiveOperations", net.minecraft.nbt.Tag.TAG_LIST)) {
            pendingOperations = tag.getList("ActiveOperations", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
            resumeCountdown = 0;
        }
        iqlEngineInstalled = tag.getBoolean("IqlEngineInstalled");
        iqlEngineRunning = !tag.contains("IqlEngineRunning") || tag.getBoolean("IqlEngineRunning");
        iqlCatalog.clear();
        final net.minecraft.nbt.ListTag catalog = tag.getList("IqlCatalog", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < catalog.size(); i++) {
            final CompoundTag entry = catalog.getCompound(i);
            iqlCatalog.put(new dev.jsc.jscomputronics.module.computing.program.iql.IqlSavedObject(
                    dev.jsc.jscomputronics.module.computing.program.iql.IqlDefinition.ObjectType
                            .valueOf(entry.getString("Type")),
                    entry.getString("Name"), entry.getString("Body"),
                    dev.jsc.jscomputronics.module.computing.program.iql.IqlDefinition.TriggerKind
                            .valueOf(entry.getString("Trigger")),
                    entry.getString("Spec")));
        }
        pausedJobs.clear();
        final net.minecraft.nbt.ListTag paused = tag.getList("PausedJobs", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < paused.size(); i++) {
            pausedJobs.add(paused.getString(i));
        }
        savedScript = tag.getString("IqlScript");
    }

    @Override
    protected void saveExtra(final CompoundTag tag, final HolderLookup.Provider registries) {
        tag.putBoolean("Failover", failoverEnabled);
        tag.putString("FailoverRole", failoverRole.name());
        tag.putInt("FailoverWaitTicks", failoverWaitTicks);
        if (nativeNetworkUuid != null) {
            tag.putString("NetworkUuid", nativeNetworkUuid.asString());
        }
        // Save the full lifetime total (persisted base plus the live dispatcher's tally);
        // the live dispatcher itself is transient, so the snapshot reloads as the new base.
        tag.putLong("CompletedTotal", completedOps());
        if (!operationLog.isEmpty()) {
            final net.minecraft.nbt.ListTag ops = new net.minecraft.nbt.ListTag();
            for (final dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord rec : operationLog) {
                ops.add(rec.toNbt(registries));
            }
            tag.put("OperationLog", ops);
        }
        // Operations in flight resume after a reload: save their state (plus any not yet resumed).
        final net.minecraft.nbt.ListTag inFlight = new net.minecraft.nbt.ListTag();
        for (final var operation : activeOperations) {
            if (operation instanceof dev.jsc.jscomputronics.module.computing.operation.PersistentOperation persistent
                    && !operation.isDone() && !persistent.isEphemeral()) {
                // A craft's machine steps read and write its in-memory pool, which does not survive a reload, so
                // they are not persisted; the parent craft re-plans and re-creates them from the handed-back pool.
                inFlight.add(persistent.saveState(registries));
            }
        }
        if (pendingOperations != null) {
            inFlight.addAll(pendingOperations);
        }
        if (!inFlight.isEmpty()) {
            tag.put("ActiveOperations", inFlight);
        }
        tag.putBoolean("IqlEngineInstalled", iqlEngineInstalled);
        tag.putBoolean("IqlEngineRunning", iqlEngineRunning);
        if (!iqlCatalog.isEmpty()) {
            final net.minecraft.nbt.ListTag catalog = new net.minecraft.nbt.ListTag();
            for (final dev.jsc.jscomputronics.module.computing.program.iql.IqlSavedObject object : iqlCatalog.all()) {
                final CompoundTag entry = new CompoundTag();
                entry.putString("Type", object.type().name());
                entry.putString("Name", object.name());
                entry.putString("Body", object.body());
                entry.putString("Trigger", object.triggerKind().name());
                entry.putString("Spec", object.triggerSpec());
                catalog.add(entry);
            }
            tag.put("IqlCatalog", catalog);
        }
        if (!pausedJobs.isEmpty()) {
            final net.minecraft.nbt.ListTag paused = new net.minecraft.nbt.ListTag();
            for (final String name : pausedJobs) {
                paused.add(net.minecraft.nbt.StringTag.valueOf(name));
            }
            tag.put("PausedJobs", paused);
        }
        if (!savedScript.isEmpty()) {
            tag.putString("IqlScript", savedScript);
        }
    }

    // --- IQL Engine (the saved-object service installed on the Mainframe) -------------------------

    public dev.jsc.jscomputronics.module.computing.program.iql.IqlCatalog iqlCatalog() {
        return iqlCatalog;
    }

    public boolean isIqlEngineInstalled() {
        return iqlEngineInstalled;
    }

    public boolean isIqlEngineRunning() {
        return iqlEngineRunning;
    }

    /** The Engine is usable only when installed, not stopped, and the Mainframe itself is powered. */
    public boolean isIqlEngineActive() {
        return iqlEngineInstalled && iqlEngineRunning && isRunning();
    }

    /** Installs the Engine on the Mainframe; returns false if it was already installed. */
    public boolean installIqlEngine() {
        if (iqlEngineInstalled) {
            return false;
        }
        iqlEngineInstalled = true;
        iqlEngineRunning = true;
        setChanged();
        return true;
    }

    /** Starts or stops the installed Engine service; returns false if there is nothing to change. */
    public boolean setIqlEngineRunning(final boolean running) {
        if (!iqlEngineInstalled || iqlEngineRunning == running) {
            return false;
        }
        iqlEngineRunning = running;
        setChanged();
        return true;
    }

    public void markIqlCatalogChanged() {
        setChanged();
    }

    // --- IQL job process control (the Processes-tab task manager) ---------------------------------

    public boolean isJobPaused(final String jobName) {
        return pausedJobs.contains(jobName.toLowerCase(java.util.Locale.ROOT));
    }

    /** Pauses a job (a resumable "End"): the agent stops firing it until it is restarted. */
    public void pauseJob(final String jobName) {
        if (pausedJobs.add(jobName.toLowerCase(java.util.Locale.ROOT))) {
            setChanged();
        }
    }

    /** Restarts a job: resumes it if paused and re-arms its trigger so it reschedules from now. */
    public void restartJob(final String jobName) {
        pausedJobs.remove(jobName.toLowerCase(java.util.Locale.ROOT));
        iqlJobAgent.rearm(jobName);
        setChanged();
    }

    /** The persisted NMS editor script for this Mainframe, or "" if none has been saved. */
    public String savedScript() {
        return savedScript;
    }

    /** Persists the NMS editor script so it survives closing and reopening the studio (and a reload). */
    public void setSavedScript(final String script) {
        this.savedScript = script == null ? "" : script;
        setChanged();
    }
}
