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
import dev.jsc.jscomputronics.common.network.DataNetworkConnectable;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
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
 * The Personal Computer BlockEntity: the player's hands-on access point to the network.
 */
public class PersonalComputerBlockEntity extends BlockEntity {

    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOT = 1;
    public static final int RAM_SLOTS_START = 2;
    public static final int RAM_SLOTS = 4;
    public static final int GPU_SLOTS_START = 6;
    public static final int GPU_SLOTS = 4;
    public static final int PSU_SLOT = 10;
    public static final int DISK_SLOTS_START = 11;
    public static final int DISK_SLOTS = 2;
    public static final int HARDWARE_SLOTS = 13;

    public static final int STORAGE_SLOTS = 18;

    private final ItemStackHandler hardware = new ItemStackHandler(HARDWARE_SLOTS) {
        @Override
        protected void onContentsChanged(final int slot) {
            buildDirty = true;
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
            return 1;
        }
    };

    private final ItemStackHandler storage = new ItemStackHandler(STORAGE_SLOTS) {
        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
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

    public PersonalComputerBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PERSONAL_COMPUTER_BE.get(), pos, state);
    }

    public static boolean isValidForSlot(final int slot, final ItemStack stack) {
        if (slot == MOTHERBOARD_SLOT) {
            return stack.getItem() instanceof MotherboardItem;
        }
        if (slot == CPU_SLOT) {
            return stack.getItem() instanceof CpuItem;
        }
        if (slot == PSU_SLOT) {
            return stack.getItem() instanceof PsuItem;
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

    public ItemStackHandler getHardware() {
        return hardware;
    }

    public ItemStackHandler getStorage() {
        return storage;
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
        if (!(hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem motherboard)) {
            return null;
        }
        if (!(hardware.getStackInSlot(PSU_SLOT).getItem() instanceof PsuItem psu)) {
            return null;
        }
        // Every count is clamped to what the installed board exposes, so a part in
        // a slot the board does not offer is ignored (same rule as the Mainframe).
        final List<CpuSpec> cpus = new ArrayList<>();
        if (motherboard.spec().cpuSlots() >= 1
                && hardware.getStackInSlot(CPU_SLOT).getItem() instanceof CpuItem cpu) {
            cpus.add(cpu.spec());
        }
        final int ramCount = Math.min(RAM_SLOTS, motherboard.spec().ramSlots());
        final List<RamSpec> rams = new ArrayList<>();
        for (int i = 0; i < ramCount; i++) {
            if (hardware.getStackInSlot(RAM_SLOTS_START + i).getItem() instanceof RamItem ram) {
                rams.add(ram.spec());
            }
        }
        final int gpuCount = Math.min(GPU_SLOTS, motherboard.spec().pcieSlots());
        final List<GpuSpec> gpus = new ArrayList<>();
        for (int i = 0; i < gpuCount; i++) {
            if (hardware.getStackInSlot(GPU_SLOTS_START + i).getItem() instanceof GpuItem gpu) {
                gpus.add(gpu.spec());
            }
        }
        final int diskCount = Math.min(DISK_SLOTS, motherboard.spec().diskSlots());
        final List<DiskSpec> disks = new ArrayList<>();
        for (int i = 0; i < diskCount; i++) {
            if (hardware.getStackInSlot(DISK_SLOTS_START + i).getItem() instanceof DiskItem disk) {
                disks.add(disk.spec());
            }
        }
        return new ComputerBuild(motherboard.spec(), cpus, gpus, rams, psu.spec(), disks);
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
            manualOn = true;
        }
        setChanged();
    }

    public long capacity() {
        return buildValid() ? currentBuild().totalCapacity() : 0L;
    }

    public long ramBuffer() {
        return buildValid() ? currentBuild().ramBuffer() : 0L;
    }

    // Motherboard-derived slot availability
    // The installed board's spec decides how many CPU/RAM/GPU slots are usable;

    public int boardCpuSlots() {
        return hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? m.spec().cpuSlots() : 0;
    }

    public int boardRamSlots() {
        return hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? m.spec().ramSlots() : 0;
    }

    public int boardPcieSlots() {
        return hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? m.spec().pcieSlots() : 0;
    }

    public int boardDiskSlots() {
        return hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? Math.min(DISK_SLOTS, m.spec().diskSlots()) : 0;
    }

    // Network connection (passive: the PC reads its network from the cable)

    private static final long NO_CABLE = Long.MIN_VALUE;

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final PersonalComputerBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        if (!isRunning()) {
            networkUuid = null;
            return;
        }
        final long cable = adjacentCable(level);
        networkUuid = cable == NO_CABLE
                ? null
                : NetworkSystem.get(level).connectivity().networkOf(cable).orElse(null);
    }

    private long adjacentCable(final ServerLevel level) {
        for (final Direction direction : Direction.values()) {
            final BlockPos neighbor = worldPosition.relative(direction);
            if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock cable
                    && acceptsTier(cable.tier())) {
                return neighbor.asLong();
            }
        }
        return NO_CABLE;
    }

    private boolean acceptsTier(final DataTier tier) {
        return getBlockState().getBlock() instanceof DataNetworkConnectable device
                && device.acceptedCableTiers().contains(tier);
    }

    public NodeUuid nodeUuid() {
        if (nodeUuid == null) {
            nodeUuid = NodeUuid.random();
            setChanged();
        }
        return nodeUuid;
    }

    @Nullable
    public NetworkUuid networkUuid() {
        return networkUuid;
    }

    // Screen sync

    // A Personal Computer runs a single Operation queue; GPUs add parallel queues
    // only on a Mainframe/Subframe, so the PC never surfaces a queue count.
    public static final int DATA_COUNT = 6;

    private final int[] clientData = new int[DATA_COUNT];

    private int computeData(final int index) {
        return switch (index) {
            case 0 -> isRunning() ? 1 : 0;
            case 1 -> buildValid() ? 1 : 0;
            case 2 -> (int) Math.min(Integer.MAX_VALUE, capacity());
            case 3 -> (int) Math.min(Integer.MAX_VALUE, ramBuffer());
            case 4 -> autoStart ? 1 : 0;
            case 5 -> networkUuid != null ? 1 : 0;
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
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Hardware")) {
            hardware.deserializeNBT(registries, tag.getCompound("Hardware"));
        }
        if (tag.contains("Storage")) {
            storage.deserializeNBT(registries, tag.getCompound("Storage"));
        }
        manualOn = tag.getBoolean("ManualOn");
        autoStart = tag.getBoolean("AutoStart");
        if (tag.contains("NodeUuid")) {
            nodeUuid = NodeUuid.fromString(tag.getString("NodeUuid"));
        }
        buildDirty = true;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Hardware", hardware.serializeNBT(registries));
        tag.put("Storage", storage.serializeNBT(registries));
        tag.putBoolean("ManualOn", manualOn);
        tag.putBoolean("AutoStart", autoStart);
        if (nodeUuid != null) {
            tag.putString("NodeUuid", nodeUuid.asString());
        }
    }
}
