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
import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.item.CpuItem;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import dev.jsc.jscomputronics.module.computing.item.ExpansionCardItem;
import dev.jsc.jscomputronics.module.computing.item.MotherboardItem;
import dev.jsc.jscomputronics.module.computing.item.PsuItem;
import dev.jsc.jscomputronics.module.computing.item.RamItem;
import dev.jsc.jscomputronics.module.computing.os.OsDef;
import dev.jsc.jscomputronics.module.computing.os.OsRegistry;
import dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents;
import dev.jsc.jscomputronics.module.computing.os.fs.SystemLayout;
import dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.resources.ResourceLocation;
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

    // The OS is no longer stored on the block entity; it lives on the system disk's SYSTEM_OS
    // component. All OS-related state is derived at runtime by scanning the installed disk stacks.

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

    /**
     * The hardware era a board must belong to for this computer to accept it, or {@code null} when the
     * computer takes a board of any era (the default). A non-null value gates both slot insertion and the
     * computed build: a board whose era differs is neither installable nor counted. Used by the era-specific
     * Personal Computers to keep, for example, a Legacy board out of a Standard machine even though both are
     * ATX.
     */
    @Nullable
    protected HardwareEra requiredBoardEra() {
        return null;
    }

    /**
     * Whether {@code stack} is a motherboard this computer accepts: it must match an accepted form factor
     * and, when {@link #requiredBoardEra()} is set, also match that era.
     */
    protected boolean isAcceptedBoard(final ItemStack stack) {
        if (!MotherboardItem.fits(stack, acceptedFormFactors())) {
            return false;
        }
        final HardwareEra required = requiredBoardEra();
        return required == null
                || (stack.getItem() instanceof MotherboardItem board && board.spec().era() == required);
    }

    protected boolean isValidPcieCard(final ItemStack stack) {
        if (!(stack.getItem() instanceof ExpansionCardItem card)) {
            return false;
        }
        final ItemStack boardStack = hardware.getStackInSlot(layout.motherboardSlot());
        if (!(boardStack.getItem() instanceof MotherboardItem motherboard)) {
            // No board yet — accept the card so it can be pre-staged; the slot will be inoperative until a board arrives.
            return true;
        }
        return card.cardSpec().bus().compatibleWith(motherboard.spec().pcieGeneration());
    }

    public boolean isValidForSlot(final int slot, final ItemStack stack) {
        if (slot == layout.motherboardSlot()) {
            return isAcceptedBoard(stack);
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
        final ItemStack boardStack = hardware.getStackInSlot(layout.motherboardSlot());
        if (!(boardStack.getItem() instanceof MotherboardItem motherboard) || !isAcceptedBoard(boardStack)) {
            // A board this computer does not accept (wrong form factor or wrong era) yields no build, so a
            // direct setStackInSlot or a board installed before an era gate existed can never run the machine.
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

    /**
     * The hardware era of the installed motherboard, or {@code null} when no board is present. Read from the board
     * alone (no PSU needed), so the assembly GUI can adopt the era's skin the moment a board goes in.
     */
    @Nullable
    public HardwareEra installedEra() {
        return hardware.getStackInSlot(layout.motherboardSlot()).getItem() instanceof MotherboardItem m
                ? m.spec().era() : null;
    }

    /**
     * The hardware era the GUI should wear. A per-era chassis (a Vintage or Legacy computer block) fixes its era
     * regardless of what is installed, so its assembly GUI shows the right era skin even when empty; every other
     * computer takes its look from the installed board, falling back to {@code null} (the STANDARD skin) when bare.
     */
    @Nullable
    public HardwareEra displayEra() {
        return getBlockState().getBlock() instanceof dev.jsc.jscomputronics.module.computing.block.EraChassisBlock chassis
                ? chassis.chassisEra()
                : installedEra();
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

    // OS installation (shared across all computer block entities)
    // The OS lives on the system disk's SYSTEM_OS component so it travels with the disk.

    /**
     * Returns the first installed disk stack whose {@code SYSTEM_OS} component points to a
     * registered OS, or {@link ItemStack#EMPTY} when no bootable disk is present.
     *
     * <p>The system disk is defined as the first disk slot (lowest index) holding a
     * {@link DiskItem} with a {@code SYSTEM_OS} component that maps to a known {@link OsDef}.
     */
    public ItemStack systemDisk() {
        for (int i = 0; i < layout.diskCount(); i++) {
            final ItemStack stack = hardware.getStackInSlot(layout.diskStart() + i);
            if (!(stack.getItem() instanceof DiskItem)) {
                continue;
            }
            final ResourceLocation osId = stack.get(ComputingModule.SYSTEM_OS.get());
            if (osId != null && OsRegistry.getOs(osId) != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns {@code true} when a bootable system disk is present in the hardware inventory.
     */
    public boolean hasOs() {
        return !systemDisk().isEmpty();
    }

    /**
     * Returns the stacks in this computer's disk slots, in slot order. Entries may be empty or hold
     * non-disk items; callers filter as needed (used by the "This PC" disk listing).
     */
    public java.util.List<ItemStack> diskStacks() {
        final java.util.List<ItemStack> out = new java.util.ArrayList<>(layout.diskCount());
        for (int i = 0; i < layout.diskCount(); i++) {
            out.add(hardware.getStackInSlot(layout.diskStart() + i));
        }
        return out;
    }

    /** The disk stack in the given 0-based disk slot (for renaming a specific installed disk), or EMPTY. */
    public ItemStack diskInSlot(final int slot) {
        if (slot < 0 || slot >= layout.diskCount()) {
            return ItemStack.EMPTY;
        }
        return hardware.getStackInSlot(layout.diskStart() + slot);
    }

    /**
     * Returns the registry key of the OS on the system disk, or {@code null} when no bootable
     * disk is installed.
     */
    @Nullable
    public ResourceLocation installedOsId() {
        final ItemStack disk = systemDisk();
        return disk.isEmpty() ? null : disk.get(ComputingModule.SYSTEM_OS.get());
    }

    /**
     * Returns the {@link OsDef} for the OS on the system disk, or {@code null} when no bootable
     * disk is installed or the registry entry is absent.
     */
    @Nullable
    public OsDef installedOs() {
        final ResourceLocation osId = installedOsId();
        return osId != null ? OsRegistry.getOs(osId) : null;
    }

    /**
     * Returns the disk footprint of the installed OS in item-equivalents, or zero when no OS is
     * present. This is subtracted from the usable storage capacity so the OS competes for disk
     * space alongside stored data.
     */
    public long reservedByOs() {
        final OsDef os = installedOs();
        return os != null ? os.footprintItems() : 0L;
    }

    /**
     * Free weight in mB-equivalents available on the system disk for user files: the disk capacity
     * minus the stored items, the existing files, and the installed OS footprint. Returns 0 when no
     * system disk is present.
     */
    public long systemDiskFreeWeight() {
        final ItemStack disk = systemDisk();
        if (!(disk.getItem() instanceof DiskItem diskItem)) {
            return 0L;
        }
        final long capacity = diskItem.spec().capacityItems() * StorageKey.MB_EQ_PER_ITEM;
        final long storageUsed = disk
                .getOrDefault(ComputingModule.DISK_STORAGE.get(), ServerStorageContents.EMPTY).usedWeight();
        final long fsUsed = disk
                .getOrDefault(ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY).usedWeight();
        final ResourceLocation osId = disk.get(ComputingModule.SYSTEM_OS.get());
        final OsDef os = osId != null ? OsRegistry.getOs(osId) : null;
        final long osReserved = os != null ? os.footprintItems() * StorageKey.MB_EQ_PER_ITEM : 0L;
        return Math.max(0L, capacity - storageUsed - fsUsed - osReserved);
    }

    /**
     * Installs the OS identified by {@code osId} onto a disk in this computer's hardware inventory.
     *
     * <p>The method scans disk slots in order and picks the first {@link DiskItem} slot (preferring
     * one that already carries a {@code SYSTEM_OS} over a plain data disk, so re-installing the
     * same OS is idempotent). The OS footprint in mB-equivalents must fit within the chosen disk's
     * free weight ({@code capacity − DISK_STORAGE.usedWeight − FILESYSTEM.usedWeight}).
     *
     * <p>Returns {@code false} without making any change when:
     * <ul>
     *   <li>the id is unknown to the registry,</li>
     *   <li>no disk is installed in the hardware inventory, or</li>
     *   <li>the OS footprint does not fit the chosen disk's free space.</li>
     * </ul>
     *
     * Returns {@code true} on success; the component is written to the disk stack in the slot,
     * the change is persisted, and clients are notified.
     */
    public boolean installOs(final ResourceLocation osId) {
        final OsDef def = OsRegistry.getOs(osId);
        if (def == null) {
            return false;
        }
        // Find the best disk slot: prefer a slot already marked as this OS (idempotent re-install),
        // then fall back to any plain disk slot.
        int targetSlot = -1;
        for (int i = 0; i < layout.diskCount(); i++) {
            final ItemStack stack = hardware.getStackInSlot(layout.diskStart() + i);
            if (!(stack.getItem() instanceof DiskItem)) {
                continue;
            }
            final ResourceLocation existing = stack.get(ComputingModule.SYSTEM_OS.get());
            if (osId.equals(existing)) {
                // Already stamped with this OS; treat as re-install: success with no mutation.
                return true;
            }
            if (targetSlot == -1) {
                targetSlot = i;
            }
        }
        if (targetSlot == -1) {
            return false; // no disk installed
        }
        final ItemStack disk = hardware.getStackInSlot(layout.diskStart() + targetSlot);
        final long diskCapacityItems = ((DiskItem) disk.getItem()).spec().capacityItems();
        final long storageUsedWeight =
                disk.getOrDefault(ComputingModule.DISK_STORAGE.get(), ServerStorageContents.EMPTY)
                        .usedWeight();
        final long fsUsedWeight =
                disk.getOrDefault(ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY)
                        .usedWeight();
        // Free weight in mB-eq; the OS footprint occupies footprintItems * MB_EQ_PER_ITEM.
        final long freeWeight =
                diskCapacityItems * StorageKey.MB_EQ_PER_ITEM - storageUsedWeight - fsUsedWeight;
        if (def.footprintItems() * StorageKey.MB_EQ_PER_ITEM > freeWeight) {
            return false;
        }
        // Stamp the SYSTEM_OS component onto the disk stack in the slot.
        // The ItemStackHandler's backing array holds a direct reference; set on the stack and
        // then write it back via setStackInSlot so onContentsChanged fires (setChanged + build invalidation).
        final ItemStack updated = disk.copy();
        updated.set(ComputingModule.SYSTEM_OS.get(), osId);
        // A graphical desktop OS lays down the Windows-like system folder skeleton on first install
        // (Program Files, Windows, Users\Public\Desktop, ...). Terminal/network OSes get nothing.
        final List<String> systemDirs = SystemLayout.directoriesFor(def.capability());
        if (!systemDirs.isEmpty()) {
            FilesystemContents fs = updated.getOrDefault(
                    ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
            for (final String d : systemDirs) {
                fs = fs.withDir(d);
            }
            updated.set(ComputingModule.FILESYSTEM.get(), fs);
        }
        hardware.setStackInSlot(layout.diskStart() + targetSlot, updated);
        // setStackInSlot triggers onContentsChanged which calls setChanged(); also push a block update.
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /**
     * Removes the OS from the system disk. A no-op when no bootable disk is installed.
     */
    public void uninstallOs() {
        for (int i = 0; i < layout.diskCount(); i++) {
            final ItemStack stack = hardware.getStackInSlot(layout.diskStart() + i);
            if (!(stack.getItem() instanceof DiskItem)) {
                continue;
            }
            final ResourceLocation osId = stack.get(ComputingModule.SYSTEM_OS.get());
            if (osId == null) {
                continue;
            }
            // Clear the component regardless of whether the OS id is still registered: if an addon OS was
            // installed and the addon later removed, the id is unknown but the player must still be able to
            // uninstall it (otherwise they would have to physically pull the disk and risk losing its files).
            final ItemStack updated = stack.copy();
            updated.remove(ComputingModule.SYSTEM_OS.get());
            hardware.setStackInSlot(layout.diskStart() + i, updated);
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                        Block.UPDATE_CLIENTS);
            }
            return;
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
        if (build == null) {
            return 0;
        }
        return build.motherboard().peripheralPorts();
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

    @Override
    public void setRemoved() {
        super.setRemoved();
        // The block's onRemove only fires on destruction; a plain chunk unload removes the block entity
        // without it, so without this the node would stay registered in the still-loaded per-level
        // network as a phantom. onBroken is idempotent, so the destruction path running both is safe.
        if (level instanceof ServerLevel serverLevel) {
            onBroken(serverLevel);
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

    /**
     * The NBT key the hardware {@link ItemStackHandler} is stored under. Overridable so a subclass with
     * pre-existing saved worlds (the Mainframe, which historically persisted under {@code "Inventory"})
     * can keep its key and load every existing component without data migration.
     */
    protected String hardwareNbtKey() {
        return "Hardware";
    }

    protected void saveExtra(final CompoundTag tag, final HolderLookup.Provider registries) {
    }

    protected void loadExtra(final CompoundTag tag, final HolderLookup.Provider registries) {
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        final String hardwareKey = hardwareNbtKey();
        if (tag.contains(hardwareKey)) {
            hardware.deserializeNBT(registries, tag.getCompound(hardwareKey));
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
        tag.put(hardwareNbtKey(), hardware.serializeNBT(registries));
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
