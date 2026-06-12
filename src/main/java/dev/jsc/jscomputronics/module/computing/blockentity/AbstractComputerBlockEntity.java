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
import dev.jsc.jscomputronics.common.hardware.ExpansionCardSpec;
import dev.jsc.jscomputronics.common.hardware.FormFactor;
import dev.jsc.jscomputronics.common.hardware.RamSpec;
import dev.jsc.jscomputronics.common.network.DataNetworkConnectable;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.peripheral.PeripheralOwnerSupport;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.item.CpuItem;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import dev.jsc.jscomputronics.module.computing.item.ExpansionCardItem;
import dev.jsc.jscomputronics.module.computing.item.MotherboardItem;
import dev.jsc.jscomputronics.module.computing.item.PsuItem;
import dev.jsc.jscomputronics.module.computing.item.RamItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared base for every computer that is a BLOCK (Personal Computer, Mainframe, Crafting Computer, and future ones such as Subframe / Supercomputer / AI Server).
 */
public abstract class AbstractComputerBlockEntity extends BlockEntity implements PeripheralOwnerSupport {

    protected static final long NO_CABLE = Long.MIN_VALUE;

    protected final ComputerHardwareLayout layout;

    protected final ItemStackHandler hardware;

    @Nullable
    private ComputerBuild cachedBuild;
    private boolean buildDirty = true;

    private boolean manualOn;
    private boolean autoStart;

    @Nullable
    private NodeUuid nodeUuid;
    private String computerName = "";
    @Nullable
    protected NetworkUuid networkUuid;
    @Nullable
    protected NetworkUuid registeredNetwork;

    protected final Set<Long> linkedMonitors = new LinkedHashSet<>();

    protected AbstractComputerBlockEntity(final BlockEntityType<?> type, final BlockPos pos,
                                          final BlockState state, final ComputerHardwareLayout layout) {
        super(type, pos, state);
        this.layout = layout;
        this.hardware = new ItemStackHandler(layout.totalSlots()) {
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
    }

    // Hardware assembly

    protected abstract Set<FormFactor> acceptedFormFactors();

    protected boolean isValidPcieCard(final ItemStack stack) {
        return stack.getItem() instanceof ExpansionCardItem;
    }

    public boolean isValidForSlot(final int slot, final ItemStack stack) {
        if (slot == layout.motherboardSlot()) {
            return MotherboardItem.fits(stack, acceptedFormFactors());
        }
        if (slot == layout.psuSlot()) {
            return stack.getItem() instanceof PsuItem;
        }
        if (layout.isCpu(slot)) {
            return stack.getItem() instanceof CpuItem;
        }
        if (layout.isRam(slot)) {
            return stack.getItem() instanceof RamItem;
        }
        if (layout.isPcie(slot)) {
            return isValidPcieCard(stack);
        }
        if (layout.isDisk(slot)) {
            return stack.getItem() instanceof DiskItem;
        }
        return false;
    }

    public ItemStackHandler getHardware() {
        return hardware;
    }

    protected void markBuildDirty() {
        buildDirty = true;
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
        if (!(hardware.getStackInSlot(layout.motherboardSlot()).getItem() instanceof MotherboardItem motherboard)) {
            return null;
        }
        if (!(hardware.getStackInSlot(layout.psuSlot()).getItem() instanceof PsuItem psu)) {
            return null;
        }
        // Every count is clamped to what the installed board exposes, so a part in a slot the board
        // does not offer is ignored.
        final int cpuCount = Math.min(layout.cpuCount(), motherboard.spec().cpuSlots());
        final List<CpuSpec> cpus = new ArrayList<>();
        for (int i = 0; i < cpuCount; i++) {
            if (hardware.getStackInSlot(layout.cpuStart() + i).getItem() instanceof CpuItem cpu) {
                cpus.add(cpu.spec());
            }
        }
        final int ramCount = Math.min(layout.ramCount(), motherboard.spec().ramSlots());
        final List<RamSpec> rams = new ArrayList<>();
        for (int i = 0; i < ramCount; i++) {
            if (hardware.getStackInSlot(layout.ramStart() + i).getItem() instanceof RamItem ram) {
                rams.add(ram.spec());
            }
        }
        final int pcieCount = Math.min(layout.pcieCount(), motherboard.spec().pcieSlots());
        final List<ExpansionCardSpec> pcieCards = new ArrayList<>();
        for (int i = 0; i < pcieCount; i++) {
            if (hardware.getStackInSlot(layout.pcieStart() + i).getItem() instanceof ExpansionCardItem card) {
                pcieCards.add(card.cardSpec());
            }
        }
        final int diskCount = Math.min(layout.diskCount(), motherboard.spec().diskSlots());
        final List<DiskSpec> disks = new ArrayList<>();
        for (int i = 0; i < diskCount; i++) {
            if (hardware.getStackInSlot(layout.diskStart() + i).getItem() instanceof DiskItem disk) {
                disks.add(disk.spec());
            }
        }
        return new ComputerBuild(motherboard.spec(), cpus, pcieCards, rams, psu.spec(), disks);
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

    // Motherboard-derived slot availability (read from the board alone, no PSU needed,
    // so the assembly GUI lights up usable slots as soon as a board goes in).

    public int boardCpuSlots() {
        return hardware.getStackInSlot(layout.motherboardSlot()).getItem() instanceof MotherboardItem m
                ? Math.min(layout.cpuCount(), m.spec().cpuSlots()) : 0;
    }

    public int boardRamSlots() {
        return hardware.getStackInSlot(layout.motherboardSlot()).getItem() instanceof MotherboardItem m
                ? Math.min(layout.ramCount(), m.spec().ramSlots()) : 0;
    }

    public int boardPcieSlots() {
        return hardware.getStackInSlot(layout.motherboardSlot()).getItem() instanceof MotherboardItem m
                ? Math.min(layout.pcieCount(), m.spec().pcieSlots()) : 0;
    }

    public int boardDiskSlots() {
        return hardware.getStackInSlot(layout.motherboardSlot()).getItem() instanceof MotherboardItem m
                ? Math.min(layout.diskCount(), m.spec().diskSlots()) : 0;
    }

    public int installedCpus() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.cpus().size();
    }

    public int installedRam() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.rams().size();
    }

    public int installedGpus() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.gpus().size();
    }

    public int installedDisks() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.disks().size();
    }

    // Host-facing slot-count names (alias the board-derived counts), so subclasses that implement
    // ComputerTerminalHost inherit these without boilerplate.
    public int cpuSlots() {
        return boardCpuSlots();
    }

    public int ramSlots() {
        return boardRamSlots();
    }

    public int gpuSlots() {
        return boardPcieSlots();
    }

    public int diskSlots() {
        return boardDiskSlots();
    }

    // Identity & name

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

    public String customName() {
        return computerName;
    }

    public void setCustomName(final String name) {
        final String trimmed = name.strip();
        final String capped = trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
        if (!capped.equals(computerName)) {
            computerName = capped;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // Peripheral ownership — the endpoint set + standard owner methods come from
    // PeripheralOwnerSupport; only the capacity is hardware-dependent (4 monitors per GPU).

    @Override
    public Set<Long> peripheralEndpoints() {
        return linkedMonitors;
    }

    @Override
    public void markPeripheralChange() {
        setChanged();
    }

    @Override
    public int maxEndpoints() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.gpus().size() * 4;
    }

    // Network participation (default: a passive client that reads its network from a cable).
    // The Mainframe overrides this entirely (it owns and orchestrates a network).

    protected abstract void registerNode(NetworkSystem system, NetworkUuid network);

    protected abstract void unregisterNode(NetworkSystem system, NetworkUuid network);

    protected void tickNode(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        NetworkUuid resolved = null;
        if (isRunning()) {
            final long cable = adjacentCable(level);
            resolved = cable == NO_CABLE ? null : system.connectivity().networkOf(cable).orElse(null);
        }
        if (registeredNetwork != null && !registeredNetwork.equals(resolved)) {
            unregisterNode(system, registeredNetwork);
            registeredNetwork = null;
        }
        networkUuid = resolved;
        if (resolved != null) {
            registerNode(system, resolved);
            registeredNetwork = resolved;
        }
    }

    public void onBroken(final ServerLevel level) {
        if (registeredNetwork != null) {
            unregisterNode(NetworkSystem.get(level), registeredNetwork);
            registeredNetwork = null;
        }
    }

    protected long adjacentCable(final ServerLevel level) {
        for (final Direction direction : cableSearchFaces()) {
            final BlockPos neighbor = worldPosition.relative(direction);
            if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock cable
                    && acceptsTier(cable.tier())) {
                return neighbor.asLong();
            }
        }
        return NO_CABLE;
    }

    /**
     * The faces on which this computer will accept a data cable, derived from the block's
     * {@link dev.jsc.jscomputronics.common.network.DataNetworkConnectable#connectsOnFace} so the
     * device's attachment and the cable's rendered connection always agree. A standalone computer
     * reports only its rear; the Mainframe (a separate block entity) and the cluster nodes keep every
     * face.
     */
    protected java.util.List<Direction> cableSearchFaces() {
        final BlockState state = getBlockState();
        if (state.getBlock() instanceof dev.jsc.jscomputronics.common.network.DataNetworkConnectable device) {
            final java.util.List<Direction> faces = new java.util.ArrayList<>(Direction.values().length);
            for (final Direction direction : Direction.values()) {
                if (device.connectsOnFace(state, direction)) {
                    faces.add(direction);
                }
            }
            return faces;
        }
        return java.util.Arrays.asList(Direction.values());
    }

    protected boolean acceptsTier(final DataTier tier) {
        return getBlockState().getBlock() instanceof DataNetworkConnectable device
                && device.acceptedCableTiers().contains(tier);
    }

    // Console state — the Command Prompt's per-computer history and installed programs.

    private final dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console =
            new dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState();

    // Provided here (no @Override: this base does not itself declare ComputerTerminalHost) so the
    // computer subclasses that ARE hosts inherit it and satisfy the interface's console() method.
    public dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console() {
        return console;
    }

    // Persistence (common fields; subclasses add their own via the hooks)

    protected void saveExtra(final CompoundTag tag, final HolderLookup.Provider registries) {
    }

    protected void loadExtra(final CompoundTag tag, final HolderLookup.Provider registries) {
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
        if (tag.contains("Console")) {
            console.load(tag.getCompound("Console"));
        }
        loadExtra(tag, registries);
        buildDirty = true;
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
        final CompoundTag consoleTag = new CompoundTag();
        console.save(consoleTag);
        tag.put("Console", consoleTag);
        saveExtra(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        if (!computerName.isEmpty()) {
            tag.putString("ComputerName", computerName);
        }
        return tag;
    }
}
