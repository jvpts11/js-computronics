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
import dev.jsc.jscomputronics.common.operation.SelfTestOperationTask;
import dev.jsc.jscomputronics.common.persistence.NetworkRegistrySavedData;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframeStructure;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import dev.jsc.jscomputronics.module.computing.item.CpuItem;
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
public class MainframeBlockEntity extends BlockEntity {

    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOTS_START = 1;
    public static final int CPU_SLOTS = 4;
    public static final int RAM_SLOTS_START = 5;
    public static final int RAM_SLOTS = 8;
    public static final int GPU_SLOTS_START = 13;
    public static final int GPU_SLOTS = 6;
    public static final int PSU_SLOT = 19;
    public static final int TOTAL_SLOTS = 20;

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
        return false;
    }

    public ItemStackHandler getInventory() {
        return inventory;
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
        return new ComputerBuild(motherboard.spec(), cpus, gpus, rams, psu.spec());
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

    private static final long NO_CABLE = Long.MIN_VALUE;

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
        } else {
            leaveNetwork(level);
            closeDispatch();
        }
    }

    private void updateNetwork(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final ConnectivityIndex index = system.connectivity();
        final NetworkUuid own = nativeNetworkUuid();

        final long cable = adjacentCable(level);
        final NetworkUuid effective;
        final boolean conflict;
        if (cable == NO_CABLE) {
            effective = own;
            conflict = false; // with no cable this mainframe shares its network with no one
        } else {
            final Optional<NetworkUuid> segment = index.networkOf(cable);
            if (segment.isPresent()) {
                effective = segment.get();
            } else if (index.contains(cable)) {
                // Registered but UUID-less: this mainframe claims the segment.
                index.assignUuid(cable, own);
                NetworkRegistrySavedData.get(level).addNetwork(own);
                effective = own;
            } else {
                // The cable block is present (found by block-state scan) but its
                effective = own;
            }
            conflict = sharesSegmentWithAnotherMainframe(level, index, cable);
        }

        setConflict(level, conflict);
        if (conflict) {
            networkUuid = null;
            unregister(system);
        } else {
            networkUuid = effective;
            system.registerMainframe(snapshot(effective));
            registeredNetwork = effective;
        }
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
            unregister(NetworkSystem.get(serverLevel));
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

    private long adjacentCable(final ServerLevel level) {
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
        for (final long posLong : inside) {
            final BlockPos p = BlockPos.of(posLong);
            for (final Direction direction : Direction.values()) {
                final BlockPos neighbor = p.relative(direction);
                if (inside.contains(neighbor.asLong())) {
                    continue; // a face internal to the multiblock
                }
                if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock cable
                        && acceptsTier(cable.tier())) {
                    return neighbor.asLong();
                }
            }
        }
        return NO_CABLE;
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
            dispatch.close();
            dispatch = null;
            dispatchQueues = 0;
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
        return dispatch == null ? 0L : dispatch.completedCount();
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
    }
}
