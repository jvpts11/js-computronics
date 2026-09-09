/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.blockentity;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.block.DataCableBlock;
import dev.jstech.computers.block.MainframeStructure;
import dev.jstech.computers.hardware.ComputerBuild;
import dev.jstech.computers.hardware.FormFactor;
import dev.jstech.core.network.ConnectivityIndex;
import dev.jstech.core.network.IDataNetworkConnectable;
import dev.jstech.core.network.DataTier;
import dev.jstech.core.network.FailoverRole;
import dev.jstech.core.network.MainframeNode;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.operation.OperationDispatch;
import dev.jstech.core.operation.OperationPriority;
import dev.jstech.core.operation.IOperationTask;
import dev.jstech.core.operation.SelfTestOperationTask;
import dev.jstech.core.persistence.NetworkRegistrySavedData;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NetworkUuidState;
import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
        implements dev.jstech.computers.terminal.IComputerTerminalHost,
        software.bernie.geckolib.animatable.GeoBlockEntity {

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

    /*
     * the cabinet as one model: what the renderer needs to know
     *
     * The Mainframe is drawn as a single GeckoLib cabinet by its controller, with a bone per installed
     * part. The client copy of a computer only carries its name (the hardware handler is deliberately
     * not synced), so the visual state travels as three small numbers in the block update: which
     * hardware slots are filled, which disks carry a system, and the machine's own condition.
     */

    private static final int FLAG_RUNNING = 1;
    private static final int FLAG_BUILD_VALID = 2;
    private static final int FLAG_NETWORKED = 4;
    private static final int FLAG_PANEL_OFF = 8;

    private static final software.bernie.geckolib.animation.RawAnimation WORK =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.mainframe.work");

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geckoCache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    private int clientHardwareMask;
    private int clientDiskSystemMask;
    private int clientFlags;

    /** The service panel taken off, showing the card bay and everything seated in it. */
    private boolean servicePanelOff;

    /** The last visual state pushed to clients, so a tick only sends a packet when something changed. */
    private long sentVisuals = -1L;

    @Override
    public void registerControllers(
            final software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        /*
         * The roof fans and the tape reels turn while the machine is up; every other visual (installed
         * hardware, the lamps, the panel) is bone visibility set by the renderer, not animation.
         */
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "work", 0,
                state -> visualRunning() ? state.setAndContinue(WORK)
                        : software.bernie.geckolib.animation.PlayState.STOP));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geckoCache;
    }

    /** The era of this cabinet, read from its block; it picks the model and the atlas. */
    public dev.jstech.core.tier.HardwareEra mainframeEra() {
        return getBlockState().getBlock()
                instanceof dev.jstech.computers.block.MainframeBlock mainframe
                ? mainframe.era() : dev.jstech.core.tier.HardwareEra.STANDARD;
    }

    /** Whether hardware slot {@code slot} holds a part, on either side. */
    public boolean hardwareInstalled(final int slot) {
        if (slot < 0 || slot >= TOTAL_SLOTS) {
            return false;
        }
        if (level != null && level.isClientSide()) {
            return (clientHardwareMask & (1 << slot)) != 0;
        }
        return !getHardware().getStackInSlot(slot).isEmpty();
    }

    /** Whether the disk in bay {@code bay} carries a system, which is what lights its lamp. */
    public boolean diskCarriesSystem(final int bay) {
        if (bay < 0 || bay >= DISK_SLOTS) {
            return false;
        }
        if (level != null && level.isClientSide()) {
            return (clientDiskSystemMask & (1 << bay)) != 0;
        }
        return dev.jstech.computers.os.OsDisks.hasSystem(
                getHardware().getStackInSlot(DISK_SLOTS_START + bay));
    }

    public boolean visualRunning() {
        return level != null && level.isClientSide() ? (clientFlags & FLAG_RUNNING) != 0 : isRunning();
    }

    public boolean visualBuildValid() {
        return level != null && level.isClientSide() ? (clientFlags & FLAG_BUILD_VALID) != 0 : buildValid();
    }

    public boolean visualNetworked() {
        return level != null && level.isClientSide() ? (clientFlags & FLAG_NETWORKED) != 0 : networkUuid() != null;
    }

    public boolean servicePanelOff() {
        return level != null && level.isClientSide() ? (clientFlags & FLAG_PANEL_OFF) != 0 : servicePanelOff;
    }

    /** Takes the service panel off the card bay or puts it back. */
    public void toggleServicePanel() {
        servicePanelOff = !servicePanelOff;
        setChanged();
        syncVisuals();
    }

    /** Everything the renderer reads, packed so a tick can tell at a glance whether it moved. */
    private long visualState() {
        int hardware = 0;
        for (int slot = 0; slot < TOTAL_SLOTS; slot++) {
            if (!getHardware().getStackInSlot(slot).isEmpty()) {
                hardware |= 1 << slot;
            }
        }
        int systems = 0;
        for (int bay = 0; bay < DISK_SLOTS; bay++) {
            if (dev.jstech.computers.os.OsDisks.hasSystem(
                    getHardware().getStackInSlot(DISK_SLOTS_START + bay))) {
                systems |= 1 << bay;
            }
        }
        int flags = 0;
        flags |= isRunning() ? FLAG_RUNNING : 0;
        flags |= buildValid() ? FLAG_BUILD_VALID : 0;
        flags |= networkUuid() != null ? FLAG_NETWORKED : 0;
        flags |= servicePanelOff ? FLAG_PANEL_OFF : 0;
        return ((long) hardware << 8) | ((long) systems << 4) | flags;
    }

    /**
     * Pushes the cabinet's look to watching clients when it changed. Called every server tick rather
     * than from each place that installs a part or flips the power, so no path can leave the model
     * showing hardware that is no longer there.
     */
    private void syncVisualsIfChanged() {
        final long state = visualState();
        if (state != sentVisuals) {
            sentVisuals = state;
            syncVisuals();
        }
    }

    public void syncVisuals() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** The whole 3 x 2 x 2 footprint: the renderer draws the cabinet from this block alone. */
    public net.minecraft.world.phys.AABB renderBox() {
        final BlockState state = getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return new net.minecraft.world.phys.AABB(worldPosition);
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(worldPosition);
        for (final BlockPos part : MainframeStructure.allPositions(
                worldPosition, state.getValue(HorizontalDirectionalBlock.FACING))) {
            box = box.minmax(new net.minecraft.world.phys.AABB(part));
        }
        return box;
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        final long state = visualState();
        tag.putInt("VisualHardware", (int) (state >>> 8));
        tag.putInt("VisualSystems", (int) ((state >>> 4) & 0xF));
        tag.putInt("VisualFlags", (int) (state & 0xF));
        return tag;
    }

    @Override
    public void onDataPacket(final net.minecraft.network.Connection connection,
                             final net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             final HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            clientHardwareMask = tag.getInt("VisualHardware");
            clientDiskSystemMask = tag.getInt("VisualSystems");
            clientFlags = tag.getInt("VisualFlags");
        }
    }

    @Override
    public void handleUpdateTag(final CompoundTag tag, final HolderLookup.Provider registries) {
        /*
         * A chunk arriving carries the same three numbers as a live update; without this the cabinet
         * would render empty until something changed and pushed a packet.
         */
        super.handleUpdateTag(tag, registries);
        clientHardwareMask = tag.getInt("VisualHardware");
        clientDiskSystemMask = tag.getInt("VisualSystems");
        clientFlags = tag.getInt("VisualFlags");
    }

    private boolean networkConflict;
    private boolean failoverEnabled;
    private FailoverRole failoverRole = FailoverRole.NONE;
    private int failoverWaitTicks;
    @Nullable
    private NetworkUuid nativeNetworkUuid;

    @Nullable
    private OperationDispatch dispatch;
    private int dispatchQueues;
    private final dev.jstech.computers.operation.NetworkIndex networkIndex =
            new dev.jstech.computers.operation.NetworkIndex();
    private final java.util.List<dev.jstech.computers.operation.INetworkOperation>
            activeOperations = new java.util.ArrayList<>();
    private long completedTotal;
    // Operations that were in flight when the world was saved, waiting for the first booted tick to resume.
    @Nullable
    private net.minecraft.nbt.ListTag pendingOperations;
    /** The game time the pending list was written at, so the resume can tell how long it sat unresumed. */
    private long pendingSavedAt;
    private int resumeCountdown;
    // Ticks to let the storage index and the switch surveys settle after a boot before resuming operations.
    private static final int RESUME_DELAY_TICKS = 20;
    // Set when the block is being destroyed, so setRemoved can tell a break (discard) from a chunk unload (keep).
    private boolean broken;

    /*
     * The IQL Engine: a service installed on the Mainframe that holds the network's saved IQL objects
     * (views/procedures/jobs) and runs the jobs. The catalog persists with the Mainframe; the NMS only
     * opens when the Engine is installed and running. A running Mainframe runs its Engine by default.
     */
    private final dev.jstech.computers.program.iql.IqlCatalog iqlCatalog =
            new dev.jstech.computers.program.iql.IqlCatalog();
    private boolean iqlEngineInstalled;
    private boolean iqlEngineRunning = true;
    /**
     * The Automation Engine: a second service that, like the IQL Engine, lets the job agent fire the saved
     * jobs. Installing it lets the Automation Manager run jobs without the full IQL Engine / NMS stack.
     */
    private boolean automationEngineInstalled;
    private final dev.jstech.computers.program.IqlJobAgent iqlJobAgent =
            new dev.jstech.computers.program.IqlJobAgent();
    /** Jobs the player paused from the Processes tab (lowercased names); a paused job never fires. */
    private final java.util.Set<String> pausedJobs = new java.util.HashSet<>();
    /** The last script the NMS editor held, persisted so it survives closing and reopening the studio. */
    private String savedScript = "";

    private static final int OPERATION_LOG_MAX = 32;
    private static final int FAILOVER_PROMOTE_DELAY = 60;
    private final java.util.Deque<dev.jstech.computers.operation.payload.OperationRecord>
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
    protected dev.jstech.core.tier.HardwareEra requiredBoardEra() {
        return blockEra();
    }

    private dev.jstech.core.tier.HardwareEra blockEra() {
        return getBlockState().getBlock()
                instanceof dev.jstech.computers.block.MainframeBlock mf
                ? mf.era()
                : dev.jstech.core.tier.HardwareEra.STANDARD;
    }

    /*
     * Slot validity is governed by the inherited instance isValidForSlot, which the acceptedFormFactors
     * override above ties to MTX-only boards over the CPU/RAM/PCIe/disk ranges of the shared layout.
     */

    /*
     * The hardware handler is inherited; this shim keeps the historic public name so the Menu/Screen,
     * block drops and GameTests address it unchanged.
     */
    public ItemStackHandler getInventory() {
        return getHardware();
    }

    @Override
    protected String hardwareNbtKey() {
        // Preserve the historic key so every existing saved Mainframe keeps its installed hardware.
        return "Inventory";
    }

    public dev.jstech.computers.storage.LocalStore localStore() {
        final java.util.List<ItemStack> disks = new java.util.ArrayList<>(DISK_SLOTS);
        for (int i = 0; i < DISK_SLOTS; i++) {
            disks.add(getHardware().getStackInSlot(DISK_SLOTS_START + i));
        }
        return new dev.jstech.computers.storage.LocalStore(disks, this::setChanged);
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
            be.tickBuildProgress(serverLevel);
            /*
             * Before tick(), which returns early on a powered-down machine: a cabinet that was just
             * switched off still has to put its lamps out on the client.
             */
            be.syncVisualsIfChanged();
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        nativeNetworkUuid(); // the mainframe owns a network identity from placement on
        /*
         * The Mainframe runs its own tick rather than the base's node tick, so the programs a player
         * wrote are ticked here as well, and told to stop when the cabinet is switched off.
         */
        tickCannon();
        if (!isRunning()) {
            leaveNetwork(level);
            closeDispatch();
            return;
        }
        // Whatever the machine is setting up copies on while it is up, network or no network.
        dev.jstech.computers.os.install.SetupRunner.tick(this, level, worldPosition);
        updateNetwork(level);
        if (networkConflict) {
            // A contested network collapses: discard every in-flight Operation (its progress is
            closeDispatch();
            return;
        }
        if (failoverRole == FailoverRole.PASSIVE) {
            /*
             * A Passive standby holds no dispatcher and runs no Operations until it is promoted; the
             * Active member owns the network. closeDispatch settles anything left from a demotion.
             */
            closeDispatch();
            return;
        }
        if (!hasOs()) {
            /*
             * Network orchestration requires a booted OS. Without one the Mainframe holds its network
             * UUID and topology but skips the dispatcher, index, Operation processing, and the IQL job
             * agent. Any submitted Operations remain PENDING until an OS is installed; on the tick when
             * the OS becomes present the dispatcher picks them up automatically (self-healing).
             */
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

    public dev.jstech.computers.operation.NetworkIndex networkIndex() {
        return networkIndex;
    }

    /*
     * Network OWNERSHIP: the Mainframe orchestrates its own network instead of reading one from a
     * single cable, so the base's passive registerNode/unregisterNode/tickNode path is unused here.
     */

    @Override
    protected void registerNode(final NetworkSystem system, final NetworkUuid network) {
        /*
         * No-op: the Mainframe owns and orchestrates its network through updateNetwork/orchestrate
         * rather than registering as a passive member; the base's tickNode is never invoked for it.
         */
    }

    @Override
    protected void unregisterNode(final NetworkSystem system, final NetworkUuid network) {
        // No-op: ownership teardown runs through unregister(NetworkSystem)/onBroken(), not this hook.
    }

    private void updateNetwork(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final ConnectivityIndex index = system.connectivity();
        final java.util.Set<Long> cables = adjacentCables(level);
        /*
         * Bridge every cable run this Mainframe touches into one segment, so the topology connected
         * through the Mainframe is a single network.
         */
        index.bridge(cables);

        // The network already laid on a touched cable, if any, a primary's network to join, or an
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
        /*
         * No primary present, so it is gone and the network is orphaned. The lowest-positioned standby
         * takes that SAME network over after the takeover delay; the rest keep standing by.
         */
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
        // Restore the network from any prior CONFLICTED or ORPHANED state, since adopting it revives it.
        NetworkRegistrySavedData.get(level).setNetworkState(effective, NetworkUuidState.ACTIVE);
        system.registerMainframe(snapshot(effective));
        system.recordMainframePosition(effective, worldPosition.asLong());
        registeredNetwork = effective;
    }

    private void updateFailoverRole(final boolean superiorPresent) {
        if (superiorPresent) {
            failoverRole = FailoverRole.PASSIVE; // a preferred Active is running, so stand by
            failoverWaitTicks = 0;
        } else if (failoverRole == FailoverRole.PASSIVE) {
            // The Active this member was backing is gone; take over after the promotion delay.
            if (++failoverWaitTicks >= FAILOVER_PROMOTE_DELAY) {
                failoverRole = FailoverRole.ACTIVE;
                failoverWaitTicks = 0;
            }
        } else {
            // Lowest-positioned and not standing by, so own the network immediately (initial election).
            failoverRole = FailoverRole.ACTIVE;
            failoverWaitTicks = 0;
        }
    }

    public boolean submitOperation(final IOperationTask task, final OperationPriority priority) {
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
        // The LAST Mainframe out orphans the network, whatever its failover role. While another
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
                    ? "[J's Computers] NETWORK_CONFLICT: two Mainframes share one network near "
                            + worldPosition.toShortString()
                    : "[J's Computers] Network conflict resolved near " + worldPosition.toShortString();
            level.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal(message), false);
        }
        networkConflict = conflict;
    }

    /*
     * The Mainframe is a 3x2x2 multiblock, so it scans for a cable across its whole footprint and
     * bridges every touched cable into one network, replacing the base's single-cable scan.
     */
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
        /*
         * Collect EVERY cable on an external face, not just the first, because the mainframe
         * bridges all of them into its single network.
         */
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
        return getBlockState().getBlock() instanceof IDataNetworkConnectable device
                && device.acceptedCableTiers().contains(tier);
    }

    private MainframeNode snapshot(final NetworkUuid network) {
        return new MainframeNode(nodeUuid(), network, capacity(),
                FailoverRole.NONE, Optional.empty(), 0L);
    }

    // Operation dispatch (the virtual-thread runtime)

    private void runDispatch() {
        final int queues = Math.max(1, pooledQueues());
        if (dispatch == null) {
            dispatch = new OperationDispatch(queues);
            dispatchQueues = queues;
        } else if (dispatchQueues != queues) {
            /*
             * The GPU count changed (a hot-swap): resize the dispatcher's lanes in place. Rebuilding it would run
             * closeDispatch, which abandons every in-flight Operation, so pulling a GPU mid-craft would discard
             * the craft and leave its machines stranded. Resizing keeps the active Operations running untouched.
             */
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
            final int queues = Math.max(1, pooledQueues());
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
     * craft returning its pool), items the resumed Operation would move again on reload.
     */
    private void closeDispatch(final boolean keepPersistent) {
        if (dispatch != null) {
            /*
             * Fold the dying dispatcher's tally into the persisted lifetime total so the
             * completed count carries across power cycles and chunk unloads.
             */
            completedTotal += dispatch.completedCount();
            dispatch.close();
            dispatch = null;
            dispatchQueues = 0;
            /*
             * Settle every in-flight multi-tick Operation first, so a holder polling isDone() (an
             * INSERT returning leftover, a SELECT freeing its lock) recovers; then record each as
             * DISCARDED so a conflict or power-off leaves a trace in the log instead of vanishing.
             */
            for (final var operation : activeOperations) {
                if (keepPersistent && operation instanceof dev.jstech.computers.operation
                        .IPersistentOperation persistent && !persistent.isEphemeral()) {
                    continue; // already saved with the block entity; it resumes on reload
                }
                operation.abandon();
                if (operation.silent()) {
                    continue; // its record travels with the Operation it turned into
                }
                recordOperation(operation.toRecord().withStatus(
                        dev.jstech.computers.operation.payload
                                .OperationRecord.STATUS_DISCARDED));
                post(net -> new dev.jstech.core.event.IOperationLifecycleEvent.Discarded(
                        net, operation.operationId(), operation.typeId()));
            }
            activeOperations.clear();
            timing.clear();
            deferredTicks.clear();
            lastGranted = java.util.Set.of();
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
        final int slots = Math.max(1, pooledQueues());
        int used = 0;
        int queued = 0;
        for (final var operation : activeOperations) {
            if (operation.isDone()) {
                continue;
            }
            if (operation.isWaiting()) {
                queued++; // blocked on another Operation's LOCK, not streaming
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
        final int slots = Math.max(1, pooledQueues());
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
                                final java.util.List<dev.jstech.computers.operation.payload.OperationRecord.MoveRow> moves) {
        recordOperation(new dev.jstech.computers.operation.payload.OperationRecord(
                type, dev.jstech.computers.storage.StorageKey.of(icon),
                requested, moved, status, java.util.List.copyOf(moves)));
    }

    public void recordOperation(
            final dev.jstech.computers.operation.payload.OperationRecord record) {
        operationLog.addFirst(record);
        while (operationLog.size() > OPERATION_LOG_MAX) {
            operationLog.removeLast();
        }
        setChanged();
    }

    // Multi-tick network Operations (decomposed into SubOperations)

    @Nullable
    public dev.jstech.computers.operation.NetworkSelectOperation submitNetworkSelect(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final dev.jstech.computers.storage.IDataSink destination, final String destinationLabel) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jstech.computers.operation.payload.OperationRecord.TYPE_SELECT, null);
    }

    @Nullable
    public dev.jstech.computers.operation.NetworkSelectOperation submitNetworkSelect(
            final net.minecraft.world.item.Item item, final long demand,
            final dev.jstech.computers.storage.IDataSink destination, final String destinationLabel) {
        return submitNetworkSelect(dev.jstech.computers.storage.StorageKey.of(item),
                demand, destination, destinationLabel);
    }

    @Nullable
    public dev.jstech.computers.operation.NetworkSelectOperation submitNetworkSelect(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final dev.jstech.computers.storage.IDataSink destination, final String destinationLabel,
            final java.util.Set<dev.jstech.core.uuid.NodeUuid> sources) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jstech.computers.operation.payload.OperationRecord.TYPE_SELECT, sources);
    }

    @Nullable
    public dev.jstech.computers.operation.NetworkSelectOperation submitNetworkSelect(
            final net.minecraft.world.item.Item item, final long demand,
            final dev.jstech.computers.storage.IDataSink destination, final String destinationLabel,
            final java.util.Set<dev.jstech.core.uuid.NodeUuid> sources) {
        return submitNetworkSelect(dev.jstech.computers.storage.StorageKey.of(item),
                demand, destination, destinationLabel, sources);
    }

    @Nullable
    public dev.jstech.computers.operation.NetworkSelectOperation submitNetworkMove(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final dev.jstech.computers.storage.IDataSink destination, final String destinationLabel,
            final java.util.Set<dev.jstech.core.uuid.NodeUuid> sources) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jstech.computers.operation.payload.OperationRecord.TYPE_MOVE, sources);
    }

    @Nullable
    public dev.jstech.computers.operation.NetworkSelectOperation submitNetworkDelete(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final dev.jstech.computers.storage.IDataSink destination, final String destinationLabel) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jstech.computers.operation.payload.OperationRecord.TYPE_DELETE, null);
    }

    @Nullable
    public dev.jstech.computers.operation.NetworkSelectOperation submitNetworkDelete(
            final net.minecraft.world.item.Item item, final long demand,
            final dev.jstech.computers.storage.IDataSink destination, final String destinationLabel) {
        return submitNetworkDelete(dev.jstech.computers.storage.StorageKey.of(item),
                demand, destination, destinationLabel);
    }

    @Nullable
    private dev.jstech.computers.operation.NetworkSelectOperation submitPull(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final dev.jstech.computers.storage.IDataSink destination, final String destinationLabel,
            final byte recordType,
            final java.util.Set<dev.jstech.core.uuid.NodeUuid> sources) {
        /*
         * A SELECT/MOVE/DELETE also needs the dispatcher; without an OS the Operation would never tick and would
         * just pile up in activeOperations. Refuse it so callers no-op cleanly instead of accumulating dead work.
         */
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return null;
        }
        final var operation = new dev.jstech.computers.operation.NetworkSelectOperation(
                serverLevel, networkUuid(), key, demand, destination, destinationLabel, recordType,
                java.util.UUID.randomUUID(), networkIndex, ensureDispatch(), sources);
        track(operation);
        return operation;
    }

    @Nullable
    public dev.jstech.computers.operation.NetworkInsertOperation submitNetworkInsert(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final String sourceLabel) {
        /*
         * Without a booted OS the dispatcher never ticks (see tick()), so an Operation submitted here would
         * sit forever in activeOperations holding items the caller already took out of the world. Refuse it so
         * callers hit their op == null branch and return the items to the player instead of losing them.
         */
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return null;
        }
        final var operation = new dev.jstech.computers.operation.NetworkInsertOperation(
                serverLevel, networkUuid(), key, demand, sourceLabel, networkIndex, ensureDispatch());
        track(operation);
        return operation;
    }

    @Nullable
    public dev.jstech.computers.operation.NetworkInsertOperation submitNetworkInsert(
            final net.minecraft.world.item.Item item, final long demand, final String sourceLabel) {
        return submitNetworkInsert(dev.jstech.computers.storage.StorageKey.of(item),
                demand, sourceLabel);
    }

    // CRAFT: recursive autocrafting over the network's Crafting Computers

    public java.util.List<net.minecraft.core.BlockPos> craftingComputerPositions() {
        if (networkUuid() == null || !(level instanceof ServerLevel serverLevel)) {
            return java.util.List.of();
        }
        final java.util.List<net.minecraft.core.BlockPos> positions = new java.util.ArrayList<>();
        for (final var node : dev.jstech.core.network.NetworkSystem.get(serverLevel)
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
        for (final var node : dev.jstech.core.network.NetworkSystem.get(serverLevel)
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
                    instanceof dev.jstech.computers.blockentity.HbwInterfaceBlockEntity sc
                    && sc.clusterOnline()) {
                total += (int) sc.parallelCrafts();
                used += sc.craftSlotsInUse();
            }
        }
        return new int[] {used, total};
    }

    public java.util.List<dev.jstech.computers.crafting.CraftingPattern> networkPatterns() {
        final java.util.List<dev.jstech.computers.crafting.CraftingPattern> patterns =
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
    public java.util.List<dev.jstech.computers.crafting.ProcessingPattern> networkProcessingPatterns() {
        final java.util.List<dev.jstech.computers.crafting.ProcessingPattern> machines =
                new java.util.ArrayList<>();
        for (final var recipe : networkMachineRecipes()) {
            recipe.proc().ifPresent(machines::add);
        }
        return machines;
    }

    /** Every machine recipe (processing / multi-stage) the network's running Crafting Computers hold. */
    public java.util.List<dev.jstech.computers.crafting.NetworkRecipe> networkMachineRecipes() {
        final java.util.List<dev.jstech.computers.crafting.NetworkRecipe> recipes =
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
    public dev.jstech.computers.crafting.NetworkCraftOperation submitNetworkCraft(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final boolean partial, final String requesterLabel) {
        return submitNetworkCraft(key, demand, partial, requesterLabel, null);
    }

    /**
     * Same as {@link #submitNetworkCraft(dev.jstech.computers.storage.StorageKey, long,
     * boolean, String)}, but plans with one extra pattern alongside the network's Recipe ROMs. A multi-stage
     * pipeline's bench stage carries its own embedded pattern, so it must craft even when that pattern was
     * never loaded into any Recipe ROM on the network.
     */
    @Nullable
    public dev.jstech.computers.crafting.NetworkCraftOperation submitNetworkCraft(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final boolean partial, final String requesterLabel,
            @Nullable final dev.jstech.computers.crafting.CraftingPattern extraPattern) {
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null
                || demand <= 0) {
            return null;
        }
        final var stock = networkIndex.snapshot();
        /*
         * A cell that accepts a tag is settled here, against what the network holds right now, so the
         * planner and the craft itself only ever see exact items.
         */
        final var patterns = dev.jstech.computers.crafting.AnyTagResolver
                .resolveAll(networkPatterns(), stock);
        final dev.jstech.computers.crafting.CraftingPattern extra = extraPattern == null ? null
                : dev.jstech.computers.crafting.AnyTagResolver.resolve(extraPattern, stock);
        if (extra != null && !patterns.contains(extra)) {
            patterns.add(extra);
        }
        // Machine patterns take part in the plan: an ingredient no bench makes may come out of a machine.
        final var planned = dev.jstech.computers.crafting.CraftPlanning.plan(
                key, demand, partial, patterns, networkProcessingPatterns(), stock);
        if (planned == null) {
            return null; // nothing on the network makes it, or not enough of it for a full request
        }
        return submitPlannedCraft(key, demand, planned.plan(), requesterLabel, extra);
    }

    /**
     * Runs an already-made plan as a craft. The record keeps the ORIGINAL request: a scaled-down partial
     * run settles as COMPLETED_PARTIAL showing produced vs requested, exactly what the player asked to see.
     */
    @Nullable
    public dev.jstech.computers.crafting.NetworkCraftOperation submitPlannedCraft(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final dev.jstech.computers.crafting.CraftPlanner.Plan plan, final String requesterLabel,
            @Nullable final dev.jstech.computers.crafting.CraftingPattern extraPattern) {
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null
                || demand <= 0 || plan.steps().isEmpty()) {
            return null;
        }
        final var operation = new dev.jstech.computers.crafting.NetworkCraftOperation(
                serverLevel, networkUuid(), key, demand, plan, networkIndex,
                java.util.UUID.randomUUID(), craftingComputerPositions(), supercomputerPositions(),
                requesterLabel, extraPattern, this);
        track(operation);
        return operation;
    }

    /**
     * Whether anything on the network produces {@code key}: a bench pattern in a Recipe ROM, or a machine
     * recipe. The cheap answer a prompt needs at once, before the plan itself is made.
     */
    public boolean anythingMakes(final dev.jstech.computers.storage.StorageKey key) {
        for (final var pattern : networkPatterns()) {
            if (key.equals(dev.jstech.computers.storage.StorageKey.of(pattern.result()))) {
                return true;
            }
        }
        for (final var recipe : networkMachineRecipes()) {
            if (key.equals(recipe.resultKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plans a recursive craft on a virtual thread. The request shows at once as a pending craft; when the
     * plan lands, the real craft takes over with the level and the settle callback given meanwhile. Returns
     * null only when nothing on the network makes {@code key} at all, or the Mainframe cannot run crafts.
     */
    @Nullable
    private dev.jstech.computers.crafting.PendingCraftOperation submitCraftAsync(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final boolean partial, final String label) {
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel) || networkUuid() == null
                || demand <= 0 || !anythingMakes(key)) {
            return null;
        }
        final var stock = networkIndex.snapshot();
        final var patterns = dev.jstech.computers.crafting.AnyTagResolver
                .resolveAll(networkPatterns(), stock);
        final var pending = new dev.jstech.computers.crafting.PendingCraftOperation(
                this, key, demand, partial, label);
        track(pending);
        pending.start(ensureDispatch(), patterns, networkProcessingPatterns(), stock);
        return pending;
    }

    /**
     * Runs a machine recipe: feeds a {@link dev.jstech.computers.crafting.ProcessingPattern}'s
     * inputs into the matching machine (declared on a Crafting Switch) and collects its outputs back into the
     * network, until {@code demand} of the primary output is produced or the pattern times out.
     */
    public dev.jstech.computers.crafting.NetworkProcessingOperation submitNetworkProcessing(
            final dev.jstech.computers.crafting.ProcessingPattern pattern, final long demand,
            final String requesterLabel) {
        return submitNetworkProcessing(pattern, demand, requesterLabel, null);
    }

    /**
     * As above, but the step draws its inputs from and returns its outputs to {@code io} instead of the network.
     * A recursive craft passes its own pool here so its machine steps pipeline through the pool (concurrent,
     * race-free) rather than through the shared network; such a step is ephemeral and does not persist a reload.
     */
    public dev.jstech.computers.crafting.NetworkProcessingOperation submitNetworkProcessing(
            final dev.jstech.computers.crafting.ProcessingPattern pattern, final long demand,
            final String requesterLabel,
            @Nullable final dev.jstech.computers.crafting.ICraftIo io) {
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null
                || demand <= 0) {
            return null;
        }
        final var operation = new dev.jstech.computers.crafting.NetworkProcessingOperation(
                serverLevel, networkUuid(), pattern, demand, craftingComputerPositions(),
                java.util.UUID.randomUUID(), requesterLabel, io);
        track(operation);
        return operation;
    }

    /** Runs a multi-stage recipe: an ordered pipeline of bench/processing stages, one at a time. */
    public dev.jstech.computers.crafting.NetworkMultiStageOperation submitNetworkMultiStage(
            final dev.jstech.computers.crafting.MultiStagePattern pattern, final long demand,
            final String requesterLabel) {
        if (!isRunning() || !hasOs() || !(level instanceof ServerLevel) || networkUuid() == null || demand <= 0) {
            return null;
        }
        // Bench stages that accept a tag are settled against stock now, the way a plain craft's are.
        final var resolved = dev.jstech.computers.crafting.AnyTagResolver
                .resolve(pattern, networkIndex.snapshot());
        final var operation = new dev.jstech.computers.crafting.NetworkMultiStageOperation(
                this, resolved, demand, requesterLabel);
        track(operation);
        return operation;
    }

    /**
     * The single craft entry point every OS surface (the terminal, the Network Interactor, and the CLI/IQL)
     * routes a request through, so all three behave the same. If a machine recipe on the network produces
     * {@code key} directly, that recipe runs (a processing run, or a multi-stage pipeline); otherwise a recursive
     * bench-and-machine craft is planned. When a machine recipe's own inputs are not all in stock and other
     * patterns can make them, the whole tree runs as one craft with the machine as a step; if nothing can make a
     * missing input, the bare machine run delivers what the network does hold. {@code partial} applies only to
     * the recursive fallback (a machine run always delivers what it can). {@code onSettle}, if given, fires when
     * the resulting operation settles. Returns the operation, or null if nothing on the network makes {@code key}.
     */
    @Nullable
    public dev.jstech.computers.operation.INetworkOperation submitCraftRequest(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
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
    public dev.jstech.computers.operation.INetworkOperation submitCraftRequest(
            final dev.jstech.computers.storage.StorageKey key, final long demand,
            final boolean partial, final String label, @Nullable final Runnable onSettle,
            final boolean preferMultiStage) {
        for (final var recipe : networkMachineRecipes()) {
            if (!key.equals(recipe.resultKey())) {
                continue;
            }
            if (recipe.proc().isPresent()) {
                if (!inputsInStock(recipe.proc().get(), demand)) {
                    /*
                     * A machine input is missing: if other patterns can make it, run the whole tree as one craft
                     * (all-or-nothing, so partial is false here); otherwise fall through to the bare machine run.
                     */
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
        /*
         * No machine makes it directly (or multi-stage was declined): plan a recursive bench-and-machine craft.
         * The planning runs off the tick; the request is listed as pending until the plan lands.
         */
        final var op = submitCraftAsync(key, demand, partial, label);
        if (op != null && onSettle != null) {
            op.onSettle(onSettle);
        }
        return op;
    }

    /**
     * Rebuilds the storage catalog from every disk on the network: the disks are read on this tick, the
     * catalog is built on a virtual thread and swapped in whole on a later tick, when {@code onDone} runs.
     * Reads keep the old catalog meanwhile. A Mainframe that is not running rebuilds on the spot instead.
     */
    public void reindexAsync(@Nullable final Runnable onDone) {
        if (!(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        if (!isRunning()) {
            networkIndex.rebuild(serverLevel, networkUuid());
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        networkIndex.rebuildAsync(serverLevel, networkUuid(), ensureDispatch(), onDone);
    }

    /** Whether the network has a multi-stage recipe whose end result is {@code key} (so a caller can offer the
     *  player the choice between the pipeline and the flat, recursively-planned path). */
    public boolean hasMultiStageRecipe(final dev.jstech.computers.storage.StorageKey key) {
        for (final var recipe : networkMachineRecipes()) {
            if (key.equals(recipe.resultKey()) && recipe.multi().isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the network currently stocks every input a processing run producing {@code quantity} would use. */
    private boolean inputsInStock(final dev.jstech.computers.crafting.ProcessingPattern machine,
                                  final long quantity) {
        if (!(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return true;
        }
        final var storage = dev.jstech.computers.operation.NetworkStorage
                .of(serverLevel, networkUuid());
        final var primary = machine.primaryOutput();
        final long runs = ceilDiv(quantity, primary == null ? 1 : Math.max(1, primary.amount()));
        final java.util.Map<dev.jstech.computers.storage.StorageKey, Long> need =
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

    /*
     * Manual LOCK / UNLOCK: player-issued holds on a network item type that make concurrent
     * Operations WAIT, the explicit handle on storage concurrency.
     */

    public long lockType(final dev.jstech.computers.storage.StorageKey key,
                         final long demand,
                         @Nullable final java.util.Set<dev.jstech.core.uuid.NodeUuid> sources) {
        if (!isRunning() || networkUuid() == null) {
            return 0L;
        }
        return networkIndex.manualLock(key, demand, sources);
    }

    public long unlockType(final dev.jstech.computers.storage.StorageKey key) {
        return networkIndex.manualUnlock(key);
    }

    public java.util.Map<dev.jstech.computers.storage.StorageKey, Long> lockedTypes() {
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
        final long savedAt = pendingSavedAt;
        pendingSavedAt = 0L;
        /*
         * Operations that sat unresumed past the expiry have lost whoever wanted them: they are restored just far
         * enough to hand back what they held (a craft's pool goes back to storage), then discarded and logged.
         */
        final long expiry = dev.jstech.core.operation.OperationBalance.orphanedOperationsExpiryTicks();
        final boolean expired = expiry > 0L && savedAt > 0L && level.getGameTime() - savedAt >= expiry;
        final int liveBefore = activeOperations.size();
        final HolderLookup.Provider registries = level.registryAccess();
        final java.util.Map<java.util.UUID, dev.jstech.computers.operation.INetworkOperation>
                byId = new java.util.HashMap<>();
        final java.util.Set<java.util.UUID> completedStages = new java.util.HashSet<>();
        final java.util.List<dev.jstech.computers.crafting.NetworkMultiStageOperation>
                pipelines = new java.util.ArrayList<>();
        /*
         * Crafts that had machine steps in flight re-plan only once every operation is back, so the steps they
         * were running can be found by id and their output counted before the remaining demand is planned.
         */
        final java.util.List<CompoundTag> craftsOnMachines = new java.util.ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            final CompoundTag tag = saved.getCompound(i);
            final java.util.UUID savedId = tag.hasUUID(
                    dev.jstech.computers.operation.IPersistentOperation.ID_KEY)
                    ? tag.getUUID(dev.jstech.computers.operation.IPersistentOperation.ID_KEY)
                    : java.util.UUID.randomUUID();
            switch (tag.getString(dev.jstech.computers.operation.IPersistentOperation.KIND_KEY)) {
                case dev.jstech.computers.crafting.NetworkProcessingOperation.KIND -> {
                    final var op = dev.jstech.computers.crafting.NetworkProcessingOperation
                            .restore(tag, level, networkUuid(), craftingComputerPositions(), registries);
                    if (op != null) {
                        track(op);
                        byId.put(savedId, op);
                    }
                }
                case dev.jstech.computers.crafting.NetworkCraftOperation.KIND -> {
                    /*
                     * An expired craft is not re-planned after its machine steps: it hands its pool back now and
                     * is discarded with the rest, so it takes the plain restore path below.
                     */
                    if (!expired && !dev.jstech.computers.crafting.NetworkCraftOperation
                            .savedMachineSteps(tag).isEmpty()) {
                        craftsOnMachines.add(tag);
                        continue;
                    }
                    // submitNetworkCraft already registers the re-planned craft in activeOperations.
                    final var restored = dev.jstech.computers.crafting.NetworkCraftOperation
                            .restore(tag, this, level, networkUuid(), registries);
                    if (restored.operation() != null) {
                        byId.put(savedId, restored.operation());
                    } else if (restored.complete()) {
                        completedStages.add(savedId);
                    }
                }
                case dev.jstech.computers.crafting.NetworkMultiStageOperation.KIND -> {
                    final var op = dev.jstech.computers.crafting.NetworkMultiStageOperation
                            .restore(tag, this, registries);
                    if (op != null) {
                        track(op);
                        pipelines.add(op);
                        byId.put(savedId, op);
                    }
                }
                default -> { }
            }
        }
        for (final CompoundTag tag : craftsOnMachines) {
            final java.util.List<dev.jstech.computers.crafting.NetworkProcessingOperation> steps =
                    new java.util.ArrayList<>();
            for (final java.util.UUID stepId : dev.jstech.computers.crafting.NetworkCraftOperation
                    .savedMachineSteps(tag)) {
                if (byId.get(stepId)
                        instanceof dev.jstech.computers.crafting.NetworkProcessingOperation proc) {
                    steps.add(proc);
                }
            }
            dev.jstech.computers.crafting.NetworkCraftOperation
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
        if (expired) {
            for (int i = liveBefore; i < activeOperations.size(); i++) {
                activeOperations.get(i).cancel(); // the next operations tick logs each one as DISCARDED
            }
        }
        setChanged();
    }

    /**
     * Stops an in-flight Operation on request: it hands back what it held and is logged as DISCARDED on the
     * next tick. Returns false when no Operation with that id is in flight any more.
     */
    public boolean cancelOperation(final java.util.UUID id) {
        final var operation = findOperation(id);
        if (operation == null || operation.isDone()) {
            return false;
        }
        operation.cancel();
        setChanged();
        return true;
    }

    /** Work queued by an operation's settle callback that must run on a later tick (the index is current then). */
    private final java.util.List<Runnable> deferredWork = new java.util.ArrayList<>();

    /**
     * Ticks a ready Operation has spent without a queue slot, per Operation. Every aging period (a balance
     * value) of deferral lifts its effective priority one level, so a long queue of
     * higher-priority work delays a low-priority request but can never starve it outright. The count only
     * grows while the Operation is passed over and is dropped when it settles, so a starved Operation that
     * finally wins a slot keeps it instead of falling straight back behind the newcomers.
     */
    private final java.util.Map<dev.jstech.computers.operation.INetworkOperation, Integer> deferredTicks =
            new java.util.IdentityHashMap<>();
    /** The Operations the last tick granted a queue slot to; the views report the rest as PENDING. */
    private java.util.Set<dev.jstech.computers.operation.INetworkOperation> lastGranted = java.util.Set.of();
    /** Per in-flight Operation: ticks spent waiting (queued or on a lock) and ticks spent running. */
    private final java.util.Map<dev.jstech.computers.operation.INetworkOperation, int[]> timing =
            new java.util.IdentityHashMap<>();
    private static final int WAITED = 0;
    private static final int RAN = 1;
    /** The last hour of settled Operations by type, and the day's peak concurrency; RAM only. */
    private final dev.jstech.core.operation.OperationStatistics statistics =
            new dev.jstech.core.operation.OperationStatistics();

    private void countTick(final dev.jstech.computers.operation.INetworkOperation operation, final int slot) {
        final int[] counted = timing.computeIfAbsent(operation, o -> new int[2]);
        if (slot == RAN && counted[RAN] == 0) {
            post(net -> new dev.jstech.core.event.IOperationLifecycleEvent.Started(
                    net, operation.operationId(), operation.typeId()));
        }
        counted[slot]++;
    }

    /**
     * Takes an Operation into the in-flight list and announces it on the series' event bus. Every
     * submission and every resume goes through here, so the bus sees each Operation exactly once.
     */
    private void track(final dev.jstech.computers.operation.INetworkOperation operation) {
        activeOperations.add(operation);
        post(net -> new dev.jstech.core.event.IOperationLifecycleEvent.Created(
                net, operation.operationId(), operation.typeId()));
    }

    /** Posts one settled Operation's outcome: completed, failed (short), or discarded. */
    private void postSettled(final dev.jstech.computers.operation.INetworkOperation operation,
                             final dev.jstech.computers.operation.payload.OperationRecord record) {
        if (record.completed()) {
            post(net -> new dev.jstech.core.event.IOperationLifecycleEvent.Completed(net,
                    operation.operationId(), operation.typeId(), (long) record.waitedTicks() + record.ranTicks()));
        } else if (record.status() == dev.jstech.computers.operation.payload.OperationRecord.STATUS_DISCARDED) {
            post(net -> new dev.jstech.core.event.IOperationLifecycleEvent.Discarded(net,
                    operation.operationId(), operation.typeId()));
        } else {
            post(net -> new dev.jstech.core.event.IOperationLifecycleEvent.Failed(net,
                    operation.operationId(), operation.typeId(), settleReason(record)));
        }
    }

    private static String settleReason(final dev.jstech.computers.operation.payload.OperationRecord record) {
        return switch (record.status()) {
            case dev.jstech.computers.operation.payload.OperationRecord.STATUS_PARTIAL ->
                    "delivered " + record.moved() + " of " + record.requested();
            case dev.jstech.computers.operation.payload.OperationRecord.STATUS_RESOURCE_LOCKED ->
                    "timed out waiting on a locked resource";
            default -> "failed";
        };
    }

    /**
     * Posts a lifecycle event for this Mainframe's network. Without a network (a Mainframe that just left
     * one, dropping its Operations on the way out) there is nobody to tell, so nothing is built or posted.
     */
    private void post(final java.util.function.Function<NetworkUuid,
            dev.jstech.core.event.IOperationLifecycleEvent> event) {
        final NetworkUuid net = networkUuid();
        if (net != null) {
            dev.jstech.core.JsCore.events().post(event.apply(net));
        }
    }

    /** The scheduler's timing of an in-flight Operation as {@code [waited, ran]}, zeros before its first tick. */
    private int[] timingOf(final dev.jstech.computers.operation.INetworkOperation operation) {
        final int[] counted = timing.get(operation);
        return counted == null ? new int[2] : counted;
    }

    /** The rolling statistics of this Mainframe's Operations. */
    public dev.jstech.core.operation.OperationStatistics statistics() {
        return statistics;
    }

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
            lastGranted = java.util.Set.of();
            deferredTicks.clear();
            timing.clear();
            return;
        }
        // Progress lives in the Operations themselves and is saved with this block entity.
        setChanged();
        statistics.observeConcurrency(level.getGameTime(), activeOperations.size());
        /*
         * A queue processes at most the RAM buffer per tick: a buffer smaller than the CPU leaves
         * the CPU idle waiting on RAM, so the effective rate is the lesser of the two. Subframes pool
         * their share of capacity and their GPUs' queues into the Mainframe that orchestrates them.
         */
        final long effectiveCapacity = Math.min(pooledCapacity(), ramBuffer());
        final int slots = Math.max(1, pooledQueues());
        assignMachines();
        /*
         * A machine step feeds at its Crafting Computer's crafting-card throughput (card x CPU), NOT the
         * Mainframe's capacity; the card is what governs how fast any craft runs, bench or machine. That
         * throughput is SHARED among the steps one computer is driving at once, so a computer feeding three
         * machines splits its card's throughput three ways (the machine's own speed is still the ceiling).
         */
        final java.util.Map<net.minecraft.core.BlockPos, Integer> stepsPerComputer = new java.util.HashMap<>();
        for (final var operation : activeOperations) {
            if (operation instanceof dev.jstech.computers.crafting.NetworkProcessingOperation proc
                    && !proc.isDone() && !proc.isWaiting()) {
                final net.minecraft.core.BlockPos cc = proc.executorComputer();
                if (cc != null) {
                    stepsPerComputer.merge(cc, 1, Integer::sum);
                }
            }
        }
        /*
         * Iterate a snapshot: a multi-stage operation submits its sub-stage into activeOperations mid-tick,
         * which would otherwise be a concurrent modification. The new stage simply ticks next tick.
         */
        final java.util.List<dev.jstech.computers.operation.INetworkOperation> snapshot =
                new java.util.ArrayList<>(activeOperations);
        /*
         * The queue slots go to the ready Operations by effective priority (level plus aging), submission
         * order inside a level. Re-deciding every tick means a higher-priority request takes over a slot the
         * next tick instead of waiting for whatever was streaming to finish.
         */
        final java.util.List<dev.jstech.core.operation.exec.QueueArbiter.Candidate<
                dev.jstech.computers.operation.INetworkOperation>> ready = new java.util.ArrayList<>();
        for (final var operation : snapshot) {
            if (!operation.isDone() && !operation.isWaiting() && occupiesQueue(operation)) {
                ready.add(new dev.jstech.core.operation.exec.QueueArbiter.Candidate<>(
                        operation, operation.priority(), deferredTicks.getOrDefault(operation, 0)));
            }
        }
        final java.util.Set<dev.jstech.computers.operation.INetworkOperation> granted =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        granted.addAll(dev.jstech.core.operation.exec.QueueArbiter.grant(ready, slots,
                dev.jstech.core.operation.OperationBalance.priorityAgingTicks()));
        lastGranted = granted;
        for (final var operation : snapshot) {
            if (operation.isDone()) {
                continue;
            }
            if (operation.isWaiting()) {
                countTick(operation, WAITED);
                operation.tick(0L); // lock retry + timeout only; holds no queue slot
            } else if (!occupiesQueue(operation)) {
                /*
                 * A craft's machine stage runs under its Crafting Computer's thread ceiling, not a Mainframe
                 * queue: it always gets its feed and never counts against the queue budget.
                 */
                countTick(operation, RAN);
                operation.tick(machineFeedBudget(operation, effectiveCapacity, stepsPerComputer));
            } else if (granted.contains(operation)) {
                countTick(operation, RAN);
                operation.tick(machineFeedBudget(operation, effectiveCapacity, stepsPerComputer));
            } else {
                /*
                 * Ready Operations beyond the queue count stay PENDING this tick: no progress, no latency
                 * countdown, since their disks have not started reading yet. Their wait is what ages them.
                 */
                countTick(operation, WAITED);
                deferredTicks.merge(operation, 1, Integer::sum);
            }
        }
        final java.util.Iterator<dev.jstech.computers.operation.INetworkOperation> it =
                activeOperations.iterator();
        while (it.hasNext()) {
            final var operation = it.next();
            if (operation.isDone()) {
                deferredTicks.remove(operation);
                final int[] counted = timing.remove(operation);
                final int waited = counted == null ? 0 : counted[WAITED];
                final int ran = counted == null ? 0 : counted[RAN];
                /*
                 * A craft's machine steps are nested stages, not operations of their own: the parent craft logs
                 * them as its sub-operations, so don't write them to the log or the lifetime tally separately.
                 * A silent Operation (a placeholder that became a real one) leaves no trace either.
                 */
                final boolean nested = operation instanceof dev.jstech.computers.crafting
                        .NetworkProcessingOperation proc && proc.isNested();
                if (!nested && !operation.silent()) {
                    final var record = operation.toRecord().withTiming(waited, ran);
                    recordOperation(record);
                    statistics.record(level.getGameTime(), record.type(), !record.completed(), waited, ran,
                            record.moved());
                    if (record.completed()) {
                        completedTotal++; // network Operations count toward the lifetime tally too
                    }
                    postSettled(operation, record);
                }
                it.remove();
            }
        }
    }

    /**
     * Gives every running processing job a distinct physical machine, so concurrency on a machine type scales
     * with the machines actually present, so two same-type jobs never share (and jam) one block. A job keeps the
     * machine it already holds (as long as it is still there and routable); a new job claims a free one of the
     * ones its recipe can route to. A job with no free machine is flagged blocked (it waits, it does not time
     * out). The Machines tab's Max Jobs is an OPTIONAL per-type ceiling on top of this: 0 means "use them all".
     */
    private void assignMachines() {
        final java.util.Set<net.minecraft.core.BlockPos> taken = new java.util.HashSet<>();
        final java.util.Map<String, Integer> perType = new java.util.HashMap<>();
        final java.util.List<dev.jstech.computers.crafting.NetworkProcessingOperation> jobs =
                new java.util.ArrayList<>();
        for (final var operation : activeOperations) {
            if (operation instanceof dev.jstech.computers.crafting.NetworkProcessingOperation proc
                    && !proc.isDone()) {
                jobs.add(proc);
            }
        }
        /*
         * Pass 1: a job that already holds a still-valid, unclaimed machine keeps it (stable across ticks so a
         * machine is never fed by two jobs turn and turn about).
         */
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
     * crafting-card throughput (card x CPU), shared among the steps that computer drives at once; the card, not
     * the machine, sets the crafting speed. Everything else runs at the Mainframe's own orchestration capacity.
     */
    private long machineFeedBudget(final dev.jstech.computers.operation.INetworkOperation operation,
                                   final long effectiveCapacity,
                                   final java.util.Map<net.minecraft.core.BlockPos, Integer> stepsPerComputer) {
        if (operation instanceof dev.jstech.computers.crafting.NetworkProcessingOperation proc) {
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
            final dev.jstech.computers.operation.INetworkOperation operation) {
        return !(operation instanceof dev.jstech.computers.crafting.NetworkProcessingOperation proc
                && proc.isNested());
    }

    /** The operations in flight right now, for views that need the live objects rather than the log. */
    public java.util.List<dev.jstech.computers.operation.INetworkOperation> liveOperations() {
        return java.util.List.copyOf(activeOperations);
    }

    public java.util.List<dev.jstech.computers.operation.payload.OperationRecord> recentOperations() {
        return java.util.List.copyOf(operationLog);
    }

    public java.util.List<dev.jstech.computers.operation.payload.OperationRecord> activeOperationRecords() {
        final java.util.List<dev.jstech.computers.operation.payload.OperationRecord> out =
                new java.util.ArrayList<>(activeOperations.size());
        for (final var operation : activeOperations) {
            final int[] counted = timingOf(operation);
            var record = operation.liveRecord().withTiming(counted[WAITED], counted[RAN]);
            /*
             * A ready Operation the last tick did not grant a slot to is queued: the scheduler decides, the
             * view only reports it. A machine stage keeps its live status (PROCESSING while it runs): it is
             * gated by its Crafting Computer's threads, not a Mainframe queue, so it is never forced to PENDING.
             */
            if (!operation.isDone() && !operation.isWaiting() && occupiesQueue(operation)
                    && !lastGranted.contains(operation)) {
                record = record.withStatus(dev.jstech.computers.operation.payload
                        .OperationRecord.STATUS_PENDING);
            }
            out.add(record);
        }
        return out;
    }

    /** The in-flight Operation with this id, or null when it has settled or never existed. */
    @Nullable
    public dev.jstech.computers.operation.INetworkOperation findOperation(final java.util.UUID id) {
        for (final var operation : activeOperations) {
            if (id.equals(operation.operationId())) {
                return operation;
            }
        }
        return null;
    }

    /**
     * Changes the scheduling level of an in-flight Operation; the next tick re-grants the queue slots with the
     * new level. Returns false when no Operation with that id is in flight any more.
     */
    public boolean setOperationPriority(final java.util.UUID id, final OperationPriority priority) {
        final var operation = findOperation(id);
        if (operation == null || operation.isDone()) {
            return false;
        }
        operation.setPriority(priority);
        setChanged();
        return true;
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

    // IComputerTerminalHost: read-only monitoring for the Monitor terminal

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
        return pooledCapacity();
    }

    @Override
    public int computerQueues() {
        return pooledQueues();
    }

    /**
     * The orchestration capacity the scheduler runs at: this Mainframe's own plus the share every active
     * Subframe on the network lends it. Read from the network registry each tick, so a Subframe powering on
     * or off changes the rate at once.
     */
    public long pooledCapacity() {
        long total = capacity();
        if (networkUuid() != null && level instanceof ServerLevel serverLevel) {
            total += NetworkSystem.get(serverLevel).subframeCapacityOf(networkUuid());
        }
        return total;
    }

    /** The parallel queues the scheduler grants: this Mainframe's (CPU + GPUs) plus the Subframes' GPUs. */
    public int pooledQueues() {
        int total = parallelQueues();
        if (networkUuid() != null && level instanceof ServerLevel serverLevel) {
            total += NetworkSystem.get(serverLevel).subframeQueuesOf(networkUuid());
        }
        return total;
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
    public dev.jstech.computers.storage.IDataSink localStorage() {
        return new dev.jstech.computers.storage.StoreSink(localStore());
    }

    public java.util.Map<dev.jstech.computers.storage.StorageKey, Long> localSnapshot() {
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
    public int indexHealthState() {
        return networkIndex.health().state().ordinal();
    }

    @Override
    public int indexHealthTypeCount() {
        return networkIndex.health().affectedTypes().size();
    }

    @Override
    public long networkStorageUsed() {
        return networkIndex.usedWeight()
                / dev.jstech.computers.storage.StorageKey.MB_EQ_PER_ITEM;
    }

    @Override
    public long networkStorageTotal() {
        if (networkUuid == null || !(level instanceof ServerLevel serverLevel)) {
            return 0L;
        }
        // Counted in items as the racks registered them: what a megabyte holds differs by era, an item does not.
        return NetworkSystem.get(serverLevel).totalStorageItemsOf(networkUuid);
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
        /*
         * Covers both destruction and chunk-unload: stop the virtual-thread executor
         * and drop the registry snapshot, so neither leaks for a mainframe that is gone.
         * This override is authoritative; the base setRemoved's passive onBroken/unregisterNode
         * path is a no-op for the Mainframe (its node hooks are no-ops), so there is no double teardown.
         * A chunk unload keeps the resumable Operations (they are in the saved NBT); a break discards them.
         */
        closeDispatch(!broken);
        if (level instanceof ServerLevel serverLevel) {
            unregister(NetworkSystem.get(serverLevel));
        }
    }

    @Override
    protected void loadExtra(final CompoundTag tag, final HolderLookup.Provider registries) {
        /*
         * Hardware (under the "Inventory" key), ManualOn, AutoStart, NodeUuid, LinkedMonitors and
         * Console are loaded by the base; only the Mainframe-only state is restored here.
         */
        failoverEnabled = tag.getBoolean("Failover");
        servicePanelOff = tag.getBoolean("ServicePanelOff");
        /*
         * Persist the standby role + countdown so a reload mid-promotion does not reset the timer (which
         * could, with frequent chunk cycling, stop a standby from ever promoting).
         */
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
            operationLog.addLast(dev.jstech.computers.operation.payload.OperationRecord
                    .fromNbt(ops.getCompound(i), registries));
        }
        if (tag.contains("ActiveOperations", net.minecraft.nbt.Tag.TAG_LIST)) {
            pendingOperations = tag.getList("ActiveOperations", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
            pendingSavedAt = tag.getLong("ActiveOperationsSavedAt");
            resumeCountdown = 0;
        }
        iqlEngineInstalled = tag.getBoolean("IqlEngineInstalled");
        iqlEngineRunning = !tag.contains("IqlEngineRunning") || tag.getBoolean("IqlEngineRunning");
        automationEngineInstalled = tag.getBoolean("AutomationEngineInstalled");
        mirrorInstalled = tag.getBoolean("MirrorInstalled");
        shelved.clear();
        final CompoundTag shelf = tag.getCompound("MirrorShelf");
        for (final String name : shelf.getAllKeys()) {
            shelved.put(name, shelf.getString(name));
        }
        iqlCatalog.clear();
        final net.minecraft.nbt.ListTag catalog = tag.getList("IqlCatalog", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < catalog.size(); i++) {
            final CompoundTag entry = catalog.getCompound(i);
            iqlCatalog.put(new dev.jstech.computers.program.iql.IqlSavedObject(
                    dev.jstech.computers.program.iql.IqlDefinition.ObjectType
                            .valueOf(entry.getString("Type")),
                    entry.getString("Name"), entry.getString("Body"),
                    dev.jstech.computers.program.iql.IqlDefinition.TriggerKind
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
        tag.putBoolean("ServicePanelOff", servicePanelOff);
        tag.putString("FailoverRole", failoverRole.name());
        tag.putInt("FailoverWaitTicks", failoverWaitTicks);
        if (nativeNetworkUuid != null) {
            tag.putString("NetworkUuid", nativeNetworkUuid.asString());
        }
        /*
         * Save the full lifetime total (persisted base plus the live dispatcher's tally);
         * the live dispatcher itself is transient, so the snapshot reloads as the new base.
         */
        tag.putLong("CompletedTotal", completedOps());
        if (!operationLog.isEmpty()) {
            final net.minecraft.nbt.ListTag ops = new net.minecraft.nbt.ListTag();
            for (final dev.jstech.computers.operation.payload.OperationRecord rec : operationLog) {
                ops.add(rec.toNbt(registries));
            }
            tag.put("OperationLog", ops);
        }
        // Operations in flight resume after a reload: save their state (plus any not yet resumed).
        final net.minecraft.nbt.ListTag inFlight = new net.minecraft.nbt.ListTag();
        for (final var operation : activeOperations) {
            if (operation instanceof dev.jstech.computers.operation.IPersistentOperation persistent
                    && !operation.isDone() && !persistent.isEphemeral()) {
                /*
                 * A craft's machine steps read and write its in-memory pool, which does not survive a reload, so
                 * they are not persisted; the parent craft re-plans and re-creates them from the handed-back pool.
                 */
                inFlight.add(persistent.saveState(registries));
            }
        }
        if (pendingOperations != null) {
            inFlight.addAll(pendingOperations);
        }
        if (!inFlight.isEmpty()) {
            tag.put("ActiveOperations", inFlight);
            /*
             * When the saved Operations resume, the time they spent unresumed decides whether they are still
             * wanted: a list that never got to resume since it was loaded keeps its original stamp.
             */
            tag.putLong("ActiveOperationsSavedAt", pendingOperations != null && pendingSavedAt > 0L
                    ? pendingSavedAt : level != null ? level.getGameTime() : 0L);
        }
        tag.putBoolean("IqlEngineInstalled", iqlEngineInstalled);
        tag.putBoolean("IqlEngineRunning", iqlEngineRunning);
        tag.putBoolean("AutomationEngineInstalled", automationEngineInstalled);
        tag.putBoolean("MirrorInstalled", mirrorInstalled);
        if (!shelved.isEmpty()) {
            final CompoundTag shelf = new CompoundTag();
            shelved.forEach(shelf::putString);
            tag.put("MirrorShelf", shelf);
        }
        if (!iqlCatalog.isEmpty()) {
            final net.minecraft.nbt.ListTag catalog = new net.minecraft.nbt.ListTag();
            for (final dev.jstech.computers.program.iql.IqlSavedObject object : iqlCatalog.all()) {
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

    // IQL Engine (the saved-object service installed on the Mainframe)

    public dev.jstech.computers.program.iql.IqlCatalog iqlCatalog() {
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

    public boolean isAutomationEngineInstalled() {
        return automationEngineInstalled;
    }

    /** Active when installed and the Mainframe is powered; enables the job agent like the IQL Engine does. */
    public boolean isAutomationEngineActive() {
        return automationEngineInstalled && isRunning();
    }

    /** Installs the Automation Engine on the Mainframe; returns false if it was already installed. */
    public boolean installAutomationEngine() {
        if (automationEngineInstalled) {
            return false;
        }
        automationEngineInstalled = true;
        setChanged();
        return true;
    }

    // The Mirror: the package repository service every Linux computer on the network installs from.
    private boolean mirrorInstalled;

    public boolean isMirrorInstalled() {
        return mirrorInstalled;
    }

    /** Whether the Mirror serves packages: installed and the Mainframe is running. */
    public boolean isMirrorActive() {
        return mirrorInstalled && isRunning();
    }

    /** Installs the Mirror service on the Mainframe; returns false if it was already installed. */
    public boolean installMirror() {
        if (mirrorInstalled) {
            return false;
        }
        mirrorInstalled = true;
        setChanged();
        return true;
    }

    /**
     * The packages players on this network have published to the Mirror, by name.
     *
     * <p>They live with the Mainframe, not with the machine that built them: that is what a Mirror is
     * for. Each is the whole package as text, so what a player installs is exactly what the player who
     * published it could read on their own screen.
     */
    private final java.util.Map<String, String> shelved = new java.util.LinkedHashMap<>();

    /** How many packages one network's Mirror will hold, so a shelf cannot grow without end. */
    public static final int SHELF_MAX = 64;

    /** Everything on the shelf, by name. */
    public java.util.Map<String, String> shelvedPackages() {
        return java.util.Map.copyOf(shelved);
    }

    /** One of them, or null. */
    @Nullable
    public String shelvedPackage(final String name) {
        return shelved.get(name);
    }

    /**
     * Puts one on the shelf, replacing any build of it already there.
     *
     * <p>Replacing rather than refusing is deliberate: publishing again is how a player releases a fix,
     * and making them take the old one down first would only mean a moment when the network has none.
     */
    public boolean shelve(final String name, final String text) {
        if (name == null || name.isBlank() || text == null || text.isBlank()) {
            return false;
        }
        if (!shelved.containsKey(name) && shelved.size() >= SHELF_MAX) {
            return false;
        }
        shelved.put(name, text);
        setChanged();
        return true;
    }

    /** Takes one off the shelf; false when it was not there. */
    public boolean unshelve(final String name) {
        if (shelved.remove(name) == null) {
            return false;
        }
        setChanged();
        return true;
    }

    /** Removes the Mirror service; returns false if it was not installed. */
    public boolean uninstallMirror() {
        if (!mirrorInstalled) {
            return false;
        }
        mirrorInstalled = false;
        setChanged();
        return true;
    }

    /** Removes the IQL Engine service (stopping it); returns false if it was not installed. */
    public boolean uninstallIqlEngine() {
        if (!iqlEngineInstalled) {
            return false;
        }
        iqlEngineInstalled = false;
        iqlEngineRunning = false;
        setChanged();
        return true;
    }

    /** Removes the Automation Engine service; returns false if it was not installed. */
    public boolean uninstallAutomationEngine() {
        if (!automationEngineInstalled) {
            return false;
        }
        automationEngineInstalled = false;
        setChanged();
        return true;
    }

    @Override
    protected void onSystemErased() {
        super.onSystemErased();
        // The services were software on the formatted disk: a wiped Mainframe serves nothing any more.
        uninstallMirror();
        uninstallIqlEngine();
        uninstallAutomationEngine();
    }

    public void markIqlCatalogChanged() {
        setChanged();
    }

    // IQL job process control (the Processes-tab task manager)

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
