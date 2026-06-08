/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.common.hardware.CpuSpec;
import dev.jsc.jscomputronics.common.hardware.DiskSpec;
import dev.jsc.jscomputronics.common.hardware.GpuSpec;
import dev.jsc.jscomputronics.common.hardware.RamSpec;
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
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframeStructure;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import dev.jsc.jscomputronics.module.computing.item.CpuItem;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import dev.jsc.jscomputronics.module.computing.item.GpuItem;
import dev.jsc.jscomputronics.module.computing.item.MotherboardItem;
import dev.jsc.jscomputronics.module.computing.item.PsuItem;
import dev.jsc.jscomputronics.module.computing.item.RamItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Mainframe BlockEntity: the binding that turns installed hardware item stacks into a {@link ComputerBuild} and exposes the powered state, capacity and parallel-queue count.
 */
public class MainframeBlockEntity extends BlockEntity
        implements dev.jsc.jscomputronics.common.peripheral.PeripheralOwner,
        dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost {

    private final java.util.Set<Long> linkedMonitors = new java.util.LinkedHashSet<>();

    @Override
    public dev.jsc.jscomputronics.common.peripheral.PeripheralCableType cableType() {
        return dev.jsc.jscomputronics.common.peripheral.PeripheralCableType.COMPUTING;
    }

    @Override
    public java.util.List<Long> linkedEndpoints() {
        return java.util.List.copyOf(linkedMonitors);
    }

    @Override
    public int maxEndpoints() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.gpus().size() * 4;
    }

    @Override
    public void onEndpointLinked(final long endpointPos) {
        if (linkedMonitors.add(endpointPos)) {
            setChanged();
        }
    }

    @Override
    public void onEndpointUnlinked(final long endpointPos) {
        if (linkedMonitors.remove(endpointPos)) {
            setChanged();
        }
    }

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

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(final int slot) {
            buildDirty = true;
            // A power loss (PSU pulled, overload, missing CPU) drops the manual
            if (!buildValid()) {
                manualOn = false;
            } else if (autoStart) {
                manualOn = true;
            }
            setChanged();
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return isValidForSlot(slot, stack);
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 1; // one component per slot
        }
    };

    public static final int STORAGE_SLOTS = 27;

    @Nullable
    private ComputerBuild cachedBuild;
    private boolean buildDirty = true;

    private boolean manualOn;
    private boolean autoStart;

    @Nullable
    private NodeUuid nodeUuid;
    @Nullable
    private NetworkUuid networkUuid;
    private boolean networkConflict;
    @Nullable
    private NetworkUuid nativeNetworkUuid;
    @Nullable
    private NetworkUuid registeredNetwork;

    @Nullable
    private OperationDispatch dispatch;
    private int dispatchQueues;
    private final dev.jsc.jscomputronics.module.computing.operation.NetworkIndex networkIndex =
            new dev.jsc.jscomputronics.module.computing.operation.NetworkIndex();
    private final java.util.List<dev.jsc.jscomputronics.module.computing.operation.NetworkOperation>
            activeOperations = new java.util.ArrayList<>();
    private long completedTotal;

    private static final int OPERATION_LOG_MAX = 32;
    private final java.util.Deque<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord>
            operationLog = new java.util.ArrayDeque<>();

    public MainframeBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.MAINFRAME_BE.get(), pos, state);
    }

    public static boolean isValidForSlot(final int slot, final ItemStack stack) {
        if (slot == MOTHERBOARD_SLOT) {
            return stack.getItem() instanceof MotherboardItem;
        }
        if (slot == PSU_SLOT) {
            return stack.getItem() instanceof PsuItem;
        }
        if (slot >= CPU_SLOTS_START && slot < CPU_SLOTS_START + CPU_SLOTS) {
            return stack.getItem() instanceof CpuItem;
        }
        if (slot >= RAM_SLOTS_START && slot < RAM_SLOTS_START + RAM_SLOTS) {
            return stack.getItem() instanceof RamItem;
        }
        if (slot >= GPU_SLOTS_START && slot < GPU_SLOTS_START + GPU_SLOTS) {
            return stack.getItem() instanceof GpuItem;
        }
        if (slot >= DISK_SLOTS_START && slot < DISK_SLOTS_START + DISK_SLOTS) {
            return stack.getItem() instanceof DiskItem;
        }
        return false;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public dev.jsc.jscomputronics.module.computing.storage.LocalStore localStore() {
        final java.util.List<ItemStack> disks = new java.util.ArrayList<>(DISK_SLOTS);
        for (int i = 0; i < DISK_SLOTS; i++) {
            disks.add(inventory.getStackInSlot(DISK_SLOTS_START + i));
        }
        return new dev.jsc.jscomputronics.module.computing.storage.LocalStore(disks, this::setChanged);
    }

    @Nullable
    public ComputerBuild currentBuild() {
        if (buildDirty) {
            cachedBuild = computeBuild();
            buildDirty = false;
        }
        return cachedBuild;
    }

    @Nullable
    private ComputerBuild computeBuild() {
        if (!(inventory.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem motherboard)) {
            return null;
        }
        if (!(inventory.getStackInSlot(PSU_SLOT).getItem() instanceof PsuItem psu)) {
            return null;
        }
        // The motherboard bounds how many of each component count — a part sitting
        final int cpuCount = Math.min(CPU_SLOTS, motherboard.spec().cpuSlots());
        final List<CpuSpec> cpus = new ArrayList<>();
        for (int i = 0; i < cpuCount; i++) {
            if (inventory.getStackInSlot(CPU_SLOTS_START + i).getItem() instanceof CpuItem cpu) {
                cpus.add(cpu.spec());
            }
        }
        final int ramCount = Math.min(RAM_SLOTS, motherboard.spec().ramSlots());
        final List<RamSpec> rams = new ArrayList<>();
        for (int i = 0; i < ramCount; i++) {
            if (inventory.getStackInSlot(RAM_SLOTS_START + i).getItem() instanceof RamItem ram) {
                rams.add(ram.spec());
            }
        }
        final int gpuCount = Math.min(GPU_SLOTS, motherboard.spec().pcieSlots());
        final List<GpuSpec> gpus = new ArrayList<>();
        for (int i = 0; i < gpuCount; i++) {
            if (inventory.getStackInSlot(GPU_SLOTS_START + i).getItem() instanceof GpuItem gpu) {
                gpus.add(gpu.spec());
            }
        }
        final int diskCount = Math.min(DISK_SLOTS, motherboard.spec().diskSlots());
        final List<DiskSpec> disks = new ArrayList<>();
        for (int i = 0; i < diskCount; i++) {
            if (inventory.getStackInSlot(DISK_SLOTS_START + i).getItem() instanceof DiskItem disk) {
                disks.add(disk.spec());
            }
        }
        return new ComputerBuild(motherboard.spec(), cpus, gpus, rams, psu.spec(), disks);
    }

    public int boardCpuSlots() {
        return inventory.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? Math.min(CPU_SLOTS, m.spec().cpuSlots()) : 0;
    }

    public int boardRamSlots() {
        return inventory.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? Math.min(RAM_SLOTS, m.spec().ramSlots()) : 0;
    }

    public int boardPcieSlots() {
        return inventory.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? Math.min(GPU_SLOTS, m.spec().pcieSlots()) : 0;
    }

    public int boardDiskSlots() {
        return inventory.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? Math.min(DISK_SLOTS, m.spec().diskSlots()) : 0;
    }

    public long storageItems() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0L : build.totalStorageItems();
    }

    public boolean buildValid() {
        final ComputerBuild build = currentBuild();
        return build != null && build.isPowered();
    }

    public boolean isRunning() {
        return buildValid() && manualOn;
    }

    public boolean isManualOn() {
        return manualOn;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void togglePower() {
        manualOn = !manualOn;
        setChanged();
    }

    public void toggleAutoStart() {
        autoStart = !autoStart;
        if (autoStart && buildValid()) {
            manualOn = true; // enabling auto-start powers a valid build at once
        }
        setChanged();
    }

    public long capacity() {
        return buildValid() ? currentBuild().totalCapacity() : 0L;
    }

    public int parallelQueues() {
        return buildValid() ? currentBuild().parallelQueues() : 0;
    }

    public long ramBuffer() {
        return buildValid() ? currentBuild().ramBuffer() : 0L;
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
        if (isRunning()) {
            updateNetwork(level);
            runDispatch();
            // Refresh the in-RAM storage catalog once per tick from the network's servers.
            networkIndex.rebuild(level, networkUuid());
            tickOperations();
        } else {
            leaveNetwork(level);
            closeDispatch();
        }
    }

    public dev.jsc.jscomputronics.module.computing.operation.NetworkIndex networkIndex() {
        return networkIndex;
    }

    private void updateNetwork(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final ConnectivityIndex index = system.connectivity();
        final NetworkUuid own = nativeNetworkUuid();

        // Every cable touching ANY external face of the multiblock — not just the
        // first one found — so a cable on any side joins the mainframe's network.
        final java.util.Set<Long> cables = adjacentCables(level);

        final NetworkUuid effective;
        final boolean conflict;
        if (cables.isEmpty()) {
            effective = own;
            conflict = false; // with no cable this mainframe shares its network with no one
        } else {
            // Adopt the network of any touched cable that already carries one (so a
            NetworkUuid existing = null;
            for (final long cable : cables) {
                final Optional<NetworkUuid> segment = index.networkOf(cable);
                if (segment.isPresent()) {
                    existing = segment.get();
                    break;
                }
            }
            effective = existing != null ? existing : own;
            if (existing == null) {
                NetworkRegistrySavedData.get(level).addNetwork(own);
            }
            boolean shared = false;
            for (final long cable : cables) {
                if (sharesSegmentWithAnotherMainframe(level, index, cable)) {
                    shared = true;
                    break;
                }
            }
            conflict = shared;
        }

        setConflict(level, conflict);
        if (conflict) {
            networkUuid = null;
            unregister(system);
            return;
        }

        // Pull every touched cable component into the one effective network. A
        for (final long cable : cables) {
            if (index.contains(cable) && !effective.equals(index.networkOf(cable).orElse(null))) {
                index.assignUuid(cable, effective);
            }
        }

        networkUuid = effective;
        system.registerMainframe(snapshot(effective));
        system.recordMainframePosition(effective, worldPosition.asLong());
        registeredNetwork = effective;
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
        if (level instanceof ServerLevel serverLevel) {
            eraseOwnedNetwork(serverLevel);
            unregister(NetworkSystem.get(serverLevel));
        }
    }

    private void eraseOwnedNetwork(final ServerLevel level) {
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
            index.clearNetwork(net);
            registry.removeNetwork(net);
        }
    }

    private boolean sharesSegmentWithAnotherMainframe(final ServerLevel level,
                                                      final ConnectivityIndex index,
                                                      final long anchorCable) {
        for (final long cablePos : index.componentPositions(anchorCable)) {
            final BlockPos base = BlockPos.of(cablePos);
            for (final Direction direction : Direction.values()) {
                final BlockPos neighbor = base.relative(direction);
                if (neighbor.equals(worldPosition)) {
                    continue;
                }
                if (level.getBlockEntity(neighbor) instanceof MainframeBlockEntity other
                        && other.isRunning()) {
                    return true;
                }
            }
        }
        return false;
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

    private java.util.Set<Long> adjacentCables(final ServerLevel level) {
        // The Mainframe is a 3x2x2 multiblock; a cable may touch any external face of
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

    private boolean acceptsTier(final DataTier tier) {
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
        if (dispatch == null || dispatchQueues != queues) {
            closeDispatch();
            dispatch = new OperationDispatch(queues);
            dispatchQueues = queues;
        }
        dispatch.tick();
    }

    private void closeDispatch() {
        if (dispatch != null) {
            // Fold the dying dispatcher's tally into the persisted lifetime total so the
            // completed count carries across power cycles and chunk unloads.
            completedTotal += dispatch.completedCount();
            dispatch.close();
            dispatch = null;
            dispatchQueues = 0;
            // Settle every in-flight multi-tick Operation first, so a holder polling isDone() (an
            for (final var operation : activeOperations) {
                operation.abandon();
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
        return dispatch == null ? 0 : dispatch.pendingCount();
    }

    public int runningOps() {
        return dispatch == null ? 0 : dispatch.runningCount();
    }

    public long completedOps() {
        return completedTotal + (dispatch == null ? 0L : dispatch.completedCount());
    }

    public void recordOperation(final byte type, final ItemStack icon, final long requested,
                                final long moved, final byte status,
                                final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.MoveRow> moves) {
        recordOperation(new dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord(
                type, icon, requested, moved, status, java.util.List.copyOf(moves)));
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
            final net.neoforged.neoforge.items.IItemHandler destination, final String destinationLabel) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_SELECT, null);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkSelect(
            final net.minecraft.world.item.Item item, final long demand,
            final net.neoforged.neoforge.items.IItemHandler destination, final String destinationLabel) {
        return submitNetworkSelect(dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item),
                demand, destination, destinationLabel);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkSelect(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final net.neoforged.neoforge.items.IItemHandler destination, final String destinationLabel,
            final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_SELECT, sources);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkSelect(
            final net.minecraft.world.item.Item item, final long demand,
            final net.neoforged.neoforge.items.IItemHandler destination, final String destinationLabel,
            final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        return submitNetworkSelect(dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item),
                demand, destination, destinationLabel, sources);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkMove(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final net.neoforged.neoforge.items.IItemHandler destination, final String destinationLabel,
            final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_MOVE, sources);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkDelete(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final net.neoforged.neoforge.items.IItemHandler destination, final String destinationLabel) {
        return submitPull(key, demand, destination, destinationLabel,
                dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_DELETE, null);
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitNetworkDelete(
            final net.minecraft.world.item.Item item, final long demand,
            final net.neoforged.neoforge.items.IItemHandler destination, final String destinationLabel) {
        return submitNetworkDelete(dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item),
                demand, destination, destinationLabel);
    }

    @Nullable
    private dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation submitPull(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final net.neoforged.neoforge.items.IItemHandler destination, final String destinationLabel,
            final byte recordType,
            final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources) {
        if (!isRunning() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return null;
        }
        final var operation = new dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation(
                serverLevel, networkUuid(), key, demand, destination, destinationLabel, recordType,
                java.util.UUID.randomUUID(), networkIndex, sources);
        activeOperations.add(operation);
        return operation;
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation submitNetworkInsert(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long demand,
            final String sourceLabel) {
        if (!isRunning() || !(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return null;
        }
        final var operation = new dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation(
                serverLevel, networkUuid(), key, demand, sourceLabel, networkIndex);
        activeOperations.add(operation);
        return operation;
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation submitNetworkInsert(
            final net.minecraft.world.item.Item item, final long demand, final String sourceLabel) {
        return submitNetworkInsert(dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item),
                demand, sourceLabel);
    }

    private void tickOperations() {
        if (activeOperations.isEmpty()) {
            return;
        }
        // The Mainframe processes at most its RAM buffer per tick: a buffer smaller than the CPU
        // leaves the CPU idle waiting on RAM, so the capacity it splits is the lesser of the two.
        final long effectiveCapacity = Math.min(capacity(), ramBuffer());
        final long[] shares = dev.jsc.jscomputronics.common.operation.exec.EqualShare.split(
                effectiveCapacity, activeOperations.size());
        for (int i = 0; i < activeOperations.size(); i++) {
            activeOperations.get(i).tick(shares[i]);
        }
        final java.util.Iterator<dev.jsc.jscomputronics.module.computing.operation.NetworkOperation> it =
                activeOperations.iterator();
        while (it.hasNext()) {
            final var operation = it.next();
            if (operation.isDone()) {
                recordOperation(operation.toRecord());
                it.remove();
            }
        }
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> recentOperations() {
        return java.util.List.copyOf(operationLog);
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> activeOperationRecords() {
        final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> out =
                new java.util.ArrayList<>(activeOperations.size());
        for (final var operation : activeOperations) {
            out.add(operation.liveRecord());
        }
        return out;
    }

    public boolean hasActiveOperations() {
        return !activeOperations.isEmpty();
    }

    public NodeUuid nodeUuid() {
        if (nodeUuid == null) {
            nodeUuid = NodeUuid.random();
            setChanged();
        }
        return nodeUuid;
    }

    public NetworkUuid nativeNetworkUuid() {
        if (nativeNetworkUuid == null) {
            nativeNetworkUuid = NetworkUuid.random();
            setChanged();
        }
        return nativeNetworkUuid;
    }

    @Nullable
    public NetworkUuid networkUuid() {
        return networkUuid;
    }

    public boolean hasNetworkConflict() {
        return networkConflict;
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
        return networkConflict ? 2 : (networkUuid != null ? 1 : 0);
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
    public int installedCpus() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.cpus().size();
    }

    @Override
    public int cpuSlots() {
        return boardCpuSlots();
    }

    @Override
    public int installedRam() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.rams().size();
    }

    @Override
    public int ramSlots() {
        return boardRamSlots();
    }

    @Override
    public int installedGpus() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.gpus().size();
    }

    @Override
    public int gpuSlots() {
        return boardPcieSlots();
    }

    @Override
    public int installedDisks() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.disks().size();
    }

    @Override
    public int diskSlots() {
        return boardDiskSlots();
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
    public net.neoforged.neoforge.items.IItemHandler localStorage() {
        return new dev.jsc.jscomputronics.module.computing.storage.LocalStoreSink(localStore());
    }

    public java.util.Map<dev.jsc.jscomputronics.module.computing.storage.StorageKey, Long> localSnapshot() {
        return localStore().view();
    }

    @Override
    public int usableStorageSlots() {
        final ComputerBuild build = currentBuild();
        if (build == null) {
            return 0;
        }
        final long capacity = build.totalStorageItems();
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

    public static final int DATA_COUNT = 11;

    private final int[] clientData = new int[DATA_COUNT];

    private int computeData(final int index) {
        return switch (index) {
            case 0 -> isRunning() ? 1 : 0;
            case 1 -> buildValid() ? 1 : 0;
            case 2 -> (int) Math.min(Integer.MAX_VALUE, capacity());
            case 3 -> parallelQueues();
            case 4 -> (int) Math.min(Integer.MAX_VALUE, ramBuffer());
            case 5 -> autoStart ? 1 : 0;
            case 6 -> manualOn ? 1 : 0;
            case 7 -> networkConflict ? 2 : (networkUuid != null ? 1 : 0);
            case 8 -> pendingOps();
            case 9 -> runningOps();
            case 10 -> (int) Math.min(Integer.MAX_VALUE, completedOps());
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
        closeDispatch();
        if (level instanceof ServerLevel serverLevel) {
            unregister(NetworkSystem.get(serverLevel));
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        manualOn = tag.getBoolean("ManualOn");
        autoStart = tag.getBoolean("AutoStart");
        if (tag.contains("NodeUuid")) {
            nodeUuid = NodeUuid.fromString(tag.getString("NodeUuid"));
        }
        if (tag.contains("NetworkUuid")) {
            nativeNetworkUuid = NetworkUuid.fromString(tag.getString("NetworkUuid"));
        }
        linkedMonitors.clear();
        for (final long monitor : tag.getLongArray("LinkedMonitors")) {
            linkedMonitors.add(monitor);
        }
        completedTotal = tag.getLong("CompletedTotal");
        operationLog.clear();
        final net.minecraft.nbt.ListTag ops = tag.getList("OperationLog", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < ops.size() && i < OPERATION_LOG_MAX; i++) {
            operationLog.addLast(dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord
                    .fromNbt(ops.getCompound(i), registries));
        }
        buildDirty = true;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putBoolean("ManualOn", manualOn);
        tag.putBoolean("AutoStart", autoStart);
        if (nodeUuid != null) {
            tag.putString("NodeUuid", nodeUuid.asString());
        }
        if (nativeNetworkUuid != null) {
            tag.putString("NetworkUuid", nativeNetworkUuid.asString());
        }
        if (!linkedMonitors.isEmpty()) {
            tag.putLongArray("LinkedMonitors", linkedMonitors.stream().mapToLong(Long::longValue).toArray());
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
    }
}
