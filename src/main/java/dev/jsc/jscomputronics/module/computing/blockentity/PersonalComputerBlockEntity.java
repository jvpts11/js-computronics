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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Personal Computer BlockEntity: the player's hands-on access point to the network.
 */
public class PersonalComputerBlockEntity extends BlockEntity
        implements dev.jsc.jscomputronics.common.peripheral.PeripheralOwner,
        dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost {

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

    @Nullable
    private ComputerBuild cachedBuild;
    private boolean buildDirty = true;

    private boolean manualOn;
    private boolean autoStart;

    @Nullable
    private NodeUuid nodeUuid;
    private String computerName = "";
    @Nullable
    private NetworkUuid networkUuid;
    @Nullable
    private NetworkUuid registeredNetwork;

    public PersonalComputerBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PERSONAL_COMPUTER_BE.get(), pos, state);
    }

    public static boolean isValidForSlot(final int slot, final ItemStack stack) {
        if (slot == MOTHERBOARD_SLOT) {
            // A Personal Computer accepts only an ATX-form-factor board, not any motherboard.
            return MotherboardItem.fits(stack,
                    java.util.Set.of(dev.jsc.jscomputronics.common.hardware.FormFactor.ATX));
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

    public dev.jsc.jscomputronics.module.computing.storage.LocalStore localStore() {
        final java.util.List<ItemStack> disks = new java.util.ArrayList<>(DISK_SLOTS);
        for (int i = 0; i < DISK_SLOTS; i++) {
            disks.add(hardware.getStackInSlot(DISK_SLOTS_START + i));
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

    // Network metadata. The PC NEVER touches network storage directly — every

    private boolean onServerNetwork() {
        return networkUuid != null && level instanceof ServerLevel;
    }

    public int networkServerCount() {
        if (!onServerNetwork()) {
            return 0;
        }
        return NetworkSystem.get((ServerLevel) level).serversOf(networkUuid).size();
    }

    // Peripheral ownership — Monitors (and later Drives/Printers) linked over
    // Peripheral Cable. A computer hosts up to 4 monitors per installed GPU.

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

    // Motherboard-derived slot availability
    // The installed board's spec decides how many CPU/RAM/GPU slots are usable;

    public int boardCpuSlots() {
        return hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? m.spec().cpuSlots() : 0;
    }

    public int boardRamSlots() {
        return hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? Math.min(RAM_SLOTS, m.spec().ramSlots()) : 0;
    }

    public int boardPcieSlots() {
        return hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? Math.min(GPU_SLOTS, m.spec().pcieSlots()) : 0;
    }

    public int boardDiskSlots() {
        return hardware.getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem m
                ? Math.min(DISK_SLOTS, m.spec().diskSlots()) : 0;
    }

    public int usableStorageSlots() {
        final ComputerBuild build = currentBuild();
        if (build == null) {
            return 0;
        }
        final long capacity = build.totalStorageItems();
        return capacity <= 0 ? 0 : (int) Math.min(STORAGE_SLOTS, (capacity + 63) / 64);
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
        final NetworkSystem system = NetworkSystem.get(level);
        NetworkUuid resolved = null;
        if (isRunning()) {
            final long cable = adjacentCable(level);
            resolved = cable == NO_CABLE ? null : system.connectivity().networkOf(cable).orElse(null);
        }

        // Self-healing node registration: drop the old entry if the network changed
        if (registeredNetwork != null && !registeredNetwork.equals(resolved)) {
            system.unregisterPersonalComputer(registeredNetwork, nodeUuid());
            registeredNetwork = null;
        }
        networkUuid = resolved;
        if (resolved != null) {
            system.registerPersonalComputer(
                    new NetworkSystem.PersonalComputerNode(nodeUuid(), resolved, capacity(),
                            worldPosition.asLong()));
            registeredNetwork = resolved;
        }
    }

    public void onBroken(final ServerLevel level) {
        if (registeredNetwork != null) {
            NetworkSystem.get(level).unregisterPersonalComputer(registeredNetwork, nodeUuid());
            registeredNetwork = null;
        }
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

    public String customName() {
        return computerName;
    }

    public void setCustomName(final String name) {
        final String trimmed = name.strip();
        final String capped = trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
        if (!capped.equals(computerName)) {
            computerName = capped;
            setChanged();
            // Re-sync the update tag so the assembly GUI shows the new name when reopened.
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Nullable
    public NetworkUuid networkUuid() {
        return networkUuid;
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
        return networkUuid != null ? 1 : 0; // a PC never conflicts; it only reads a network
    }

    @Override
    public long orchestrationCapacity() {
        return capacity();
    }

    @Override
    public int computerQueues() {
        return isRunning() ? 1 : 0; // a PC runs a single Operation queue
    }

    @Override
    public long computerRamBuffer() {
        return ramBuffer();
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
        final ComputerBuild build = currentBuild();
        return build == null ? 0L : build.totalStorageItems();
    }

    @Override
    public dev.jsc.jscomputronics.module.computing.storage.DataSink localStorage() {
        return new dev.jsc.jscomputronics.module.computing.storage.LocalStoreSink(localStore());
    }

    public java.util.Map<dev.jsc.jscomputronics.module.computing.storage.StorageKey, Long> localSnapshot() {
        return localStore().view();
    }

    @Override
    public boolean isMainframeHost() {
        return false;
    }

    // Screen sync

    // A Personal Computer runs a single Operation queue; GPUs add parallel queues
    // only on a Mainframe/Subframe, so the PC never surfaces a queue count.
    public static final int DATA_RUNNING = 0;
    public static final int DATA_BUILD_VALID = 1;
    public static final int DATA_CAPACITY = 2;
    public static final int DATA_RAM_BUFFER = 3;
    public static final int DATA_AUTOSTART = 4;
    public static final int DATA_ON_NETWORK = 5;
    public static final int DATA_SERVER_COUNT = 6;
    public static final int DATA_COUNT = DATA_SERVER_COUNT + 1;

    private final int[] clientData = new int[DATA_COUNT];

    private int computeData(final int index) {
        return switch (index) {
            case DATA_RUNNING -> isRunning() ? 1 : 0;
            case DATA_BUILD_VALID -> buildValid() ? 1 : 0;
            case DATA_CAPACITY -> (int) Math.min(Integer.MAX_VALUE, capacity());
            case DATA_RAM_BUFFER -> (int) Math.min(Integer.MAX_VALUE, ramBuffer());
            case DATA_AUTOSTART -> autoStart ? 1 : 0;
            case DATA_ON_NETWORK -> networkUuid != null ? 1 : 0;
            case DATA_SERVER_COUNT -> networkServerCount();
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
        manualOn = tag.getBoolean("ManualOn");
        autoStart = tag.getBoolean("AutoStart");
        computerName = tag.getString("ComputerName");
        if (tag.contains("NodeUuid")) {
            nodeUuid = NodeUuid.fromString(tag.getString("NodeUuid"));
        }
        linkedMonitors.clear();
        for (final long monitor : tag.getLongArray("LinkedMonitors")) {
            linkedMonitors.add(monitor);
        }
        buildDirty = true;
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        // Sync the player-given name to the client so the assembly GUI's name field shows it.
        final CompoundTag tag = super.getUpdateTag(registries);
        if (!computerName.isEmpty()) {
            tag.putString("ComputerName", computerName);
        }
        return tag;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Hardware", hardware.serializeNBT(registries));
        tag.putBoolean("ManualOn", manualOn);
        tag.putBoolean("AutoStart", autoStart);
        if (!computerName.isEmpty()) {
            tag.putString("ComputerName", computerName);
        }
        if (nodeUuid != null) {
            tag.putString("NodeUuid", nodeUuid.asString());
        }
        if (!linkedMonitors.isEmpty()) {
            tag.putLongArray("LinkedMonitors", linkedMonitors.stream().mapToLong(Long::longValue).toArray());
        }
    }
}
