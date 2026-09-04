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
import dev.jsc.jscomputronics.module.computing.os.fs.SystemLayout;
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
public abstract class AbstractComputerBlockEntity extends BlockEntity
        implements PeripheralOwnerSupport, dev.jsc.jscomputronics.module.computing.os.OsHost {

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

    /**
     * Whether {@code stack} is a processor this machine's board can seat: the socket has to match, and
     * so does the hardware generation. A chip that physically cannot go in the socket should not go in
     * the slot either — letting it in only to refuse to boot tells the player nothing about why.
     */
    protected boolean isValidCpu(final ItemStack stack) {
        if (!(stack.getItem() instanceof CpuItem cpu)) {
            return false;
        }
        final ItemStack boardStack = hardware.getStackInSlot(layout.motherboardSlot());
        if (!(boardStack.getItem() instanceof MotherboardItem board)) {
            return true; // no board yet: allow pre-staging, as the expansion slots do
        }
        return cpu.spec().socket() == board.spec().socket()
                && cpu.spec().era() == board.spec().era();
    }

    /**
     * Whether {@code stack} is memory this machine's board takes: the board lists the RAM generations
     * its slots are keyed for, and the module must belong to the same hardware generation.
     */
    protected boolean isValidRam(final ItemStack stack) {
        if (!(stack.getItem() instanceof RamItem ram)) {
            return false;
        }
        final ItemStack boardStack = hardware.getStackInSlot(layout.motherboardSlot());
        if (!(boardStack.getItem() instanceof MotherboardItem board)) {
            return true; // no board yet: allow pre-staging
        }
        return board.spec().acceptedRam().contains(ram.spec().generation())
                && ram.spec().era() == board.spec().era();
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
            return isValidCpu(stack);
        }
        if (layout.isRam(slot)) {
            return isValidRam(stack);
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
        setPowered(!manualOn);
    }

    @Override
    public void setPowered(final boolean on) {
        manualOn = on;
        if (on) {
            needsPost = true;
        }
        openWindows.clear(); // power off or a cold start: no desktop survives either
        pendingInstallSlot = NO_PENDING_INSTALL; // nor does an installer session
        setChanged();
    }

    public void toggleAutoStart() {
        autoStart = !autoStart;
        if (autoStart && buildValid()) {
            if (!manualOn) {
                // Auto-start bringing a machine up from off is a cold start. It writes the POST flag
                // directly, so it has to close the desktop itself: a machine that went dark through an
                // invalid build never passed through setPowered, and its old windows would otherwise
                // resurface on a session that no longer exists.
                needsPost = true;
                openWindows.clear();
                pendingInstallSlot = NO_PENDING_INSTALL;
            }
            manualOn = true;
        }
        setChanged();
    }

    // The power-on self-test runs once per power-up (and once per requested reboot), then the monitor
    // boots straight into the OS. Deliberately transient: a computer that stayed on across a chunk
    // reload does not POST again, exactly like a real machine that was never switched off.
    private boolean needsPost;

    /** Whether the next monitor use should play the power-on self-test before booting. */
    public boolean needsPost() {
        return needsPost;
    }

    public void setNeedsPost(final boolean value) {
        this.needsPost = value;
        if (value) {
            openWindows.clear(); // a restart closes everything, as it does on any machine
            pendingInstallSlot = NO_PENDING_INSTALL; // the restart is what the installer was waiting for
        }
    }

    // A guided installer that finished writing the system but has not rebooted yet. Persisted: the
    // machine is still in the installer after a reload, the same way it keeps its booted desktop.
    private int pendingInstallSlot = NO_PENDING_INSTALL;

    @Override
    public int pendingInstallSlot() {
        return pendingInstallSlot;
    }

    @Override
    public void setPendingInstallSlot(final int slot) {
        this.pendingInstallSlot = slot;
        setChanged();
    }

    // The desktop this session booted into. Held apart from what is on disk so that installing or
    // removing a desktop package takes effect on the next boot, not the next time the monitor is opened.
    @Nullable
    private ResourceLocation bootedDesktopId;

    @Override
    @Nullable
    public ResourceLocation bootedDesktopId() {
        return bootedDesktopId;
    }

    @Override
    public void setBootedDesktopId(@Nullable final ResourceLocation id) {
        this.bootedDesktopId = id;
        setChanged();
    }

    // The windows open on this machine's desktop. Kept here, not in the client, so they belong to the
    // machine: whoever opens the monitor next sees them, and they survive the game being closed.
    private final java.util.List<dev.jsc.jscomputronics.module.computing.os.OpenWindow> openWindows =
            new java.util.ArrayList<>();

    @Override
    public java.util.List<dev.jsc.jscomputronics.module.computing.os.OpenWindow> openWindows() {
        return java.util.List.copyOf(openWindows);
    }

    @Override
    public void setOpenWindows(final java.util.List<dev.jsc.jscomputronics.module.computing.os.OpenWindow> windows) {
        openWindows.clear();
        for (final dev.jsc.jscomputronics.module.computing.os.OpenWindow window : windows) {
            if (openWindows.size() >= dev.jsc.jscomputronics.module.computing.os.OpenWindow.MAX) {
                break;
            }
            openWindows.add(window);
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

    /** The best (max) CPU clock in MHz across installed CPUs, or 0 when there is no valid build. */
    public int maxCpuMhz() {
        final ComputerBuild build = currentBuild();
        if (build == null) {
            return 0;
        }
        int max = 0;
        for (final CpuSpec cpu : build.cpus()) {
            max = Math.max(max, cpu.freqMhz());
        }
        return max;
    }

    /**
     * The usable VRAM in MB across installed GPUs, or 0 when there is no valid build. A card seated in
     * a slot older than itself contributes only what that slot's bandwidth allows.
     */
    public int totalVramMb() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0 : build.effectiveVramMb();
    }

    /**
     * Free space on the system disk in real MB, for the program-install disk-footprint gate: the free
     * mB-equivalent weight ({@link #systemDiskFreeWeight()}) at what an item costs on that disk's era.
     */
    public long systemDiskFreeMb() {
        final ItemStack disk = systemDisk();
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.systemDiskFreeWeight(disk)
                * diskEra(disk).mbPerItem() / StorageKey.MB_EQ_PER_ITEM;
    }

    /** The era a disk was made for — what an item and a system image cost on it; standard for no disk. */
    protected static HardwareEra diskEra(final ItemStack disk) {
        return disk.getItem() instanceof DiskItem item ? item.spec().era() : HardwareEra.STANDARD;
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
    // The firmware's preferred boot disk slot (-1 = the first disk with a system). Persisted, so dual boot sticks.
    private int bootDiskSlot = -1;

    public ItemStack systemDisk() {
        // The preferred boot disk (chosen in the firmware's boot order) wins when it holds a system; otherwise
        // the first disk with a system boots, so a computer with two installed OSes dual-boots by choice.
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.systemDisk(
                layout.diskCount(), this::diskInSlot, bootDiskSlot);
    }

    private static boolean hasSystem(final ItemStack disk) {
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.hasSystem(disk);
    }

    /** The disk slot index the firmware boots first, or {@code -1} for "the first disk with a system". */
    public int bootDiskSlot() {
        return bootDiskSlot;
    }

    /** Sets the preferred boot disk slot ({@code -1} = automatic) and marks the computer dirty. */
    public void setBootDiskSlot(final int slot) {
        this.bootDiskSlot = slot;
        setChanged();
        buildDirty = true;
    }

    /**
     * Returns {@code true} when a bootable system disk is present in the hardware inventory.
     */
    /**
     * The desktop environment this computer boots into: the OS's bundled one (the Frames editions), else the
     * first desktop-environment package installed on it (a Linux distribution after {@code apt install gnome}),
     * else null (a TTY-only or network OS).
     */
    public ResourceLocation installedDesktopId() {
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.installedDesktopId(
                installedOs(), console());
    }

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
        // One lookup of the system disk serves both the system and the era the system sits on.
        final ItemStack disk = systemDisk();
        final ResourceLocation osId = disk.isEmpty() ? null : disk.get(ComputingModule.SYSTEM_OS.get());
        final OsDef os = osId != null ? OsRegistry.getOs(osId) : null;
        return os != null ? os.footprintItemsOn(diskEra(disk)) : 0L;
    }

    /**
     * Free weight in mB-equivalents available on the system disk for user files: the disk capacity
     * minus the stored items, the existing files, and the installed OS footprint. Returns 0 when no
     * system disk is present.
     */
    public long systemDiskFreeWeight() {
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.systemDiskFreeWeight(systemDisk());
    }

    /**
     * Installs the OS identified by {@code osId} onto a disk in this computer's hardware inventory.
     *
     * <p>The method scans disk slots in order and picks the first {@link DiskItem} slot (preferring
     * one that already carries a {@code SYSTEM_OS} over a plain data disk, so re-installing the
     * same OS is idempotent). The OS footprint in mB-equivalents must fit within the chosen disk's
     * free weight ({@code capacity − stored items' weight − FILESYSTEM.usedWeight}).
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
        return installOs(osId, -1);
    }

    // Which progress quarter (25/50/75%) each running build last reported, so the console gets a handful
    // of emerge-style progress lines instead of one per second. Transient by design.
    private final java.util.Map<String, Integer> buildQuarterReported = new java.util.HashMap<>();
    private int liveKernelQuarterReported;

    /**
     * Streams source-build progress and completion to every console open on this computer (the full-screen
     * prompt and the desktop terminal window alike). Called from the host block's server ticker; checks
     * once a second and only speaks on a 25% step or on completion, like emerge's own output.
     */
    public void tickBuildProgress(final net.minecraft.server.level.ServerLevel level) {
        final dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console = console();
        if (console == null || level.getGameTime() % 20 != 0) {
            return;
        }
        final long now = level.getGameTime();
        final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.CommandOutputPayload.WireLine>
                wire = new java.util.ArrayList<>();
        final int dim = dev.jsc.jscomputronics.module.computing.program.cli.CliStyle.DIM.ordinal();
        final int ok = dev.jsc.jscomputronics.module.computing.program.cli.CliStyle.OK.ordinal();

        // Package builds (emerge): progress quarters while compiling.
        for (final java.util.Map.Entry<String, Long> entry : console.pendingBuilds().entrySet()) {
            final long total = console.buildTotal(entry.getKey());
            if (total <= 0 || entry.getValue() <= now) {
                continue; // completions are handled below
            }
            final long left = entry.getValue() - now;
            final int pct = (int) Math.max(0, Math.min(99, 100 - left * 100 / total));
            final int quarter = pct / 25;
            if (quarter >= 1 && quarter > buildQuarterReported.getOrDefault(entry.getKey(), 0)) {
                buildQuarterReported.put(entry.getKey(), quarter);
                wire.add(new dev.jsc.jscomputronics.module.computing.operation.payload.CommandOutputPayload.WireLine(
                        ">>> " + buildDisplayName(entry.getKey()) + ": compiling ... " + pct + "% ("
                                + (left / 20) + "s left)", dim));
            }
        }

        // The Gentoo live install's kernel compile gets the same treatment.
        final dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState live = console.liveInstall();
        if (live != null && live.kernelCompiling(now)) {
            final long kernelTotal = Math.max(5L, Math.min(1800L, 64_000L / Math.max(100, maxCpuMhz()))) * 20L;
            final long left = live.kernelReadyAt() - now;
            final int pct = (int) Math.max(0, Math.min(99, 100 - left * 100 / Math.max(1L, kernelTotal)));
            final int quarter = pct / 25;
            if (quarter >= 1 && quarter > liveKernelQuarterReported) {
                liveKernelQuarterReported = quarter;
                wire.add(new dev.jsc.jscomputronics.module.computing.operation.payload.CommandOutputPayload.WireLine(
                        ">>> sys-kernel/gentoo-sources: compiling ... " + pct + "% (" + (left / 20) + "s left)", dim));
            }
        } else if (live != null && live.kernelReadyAt() >= 0 && !live.kernelCompiling(now)
                && liveKernelQuarterReported > 0 && liveKernelQuarterReported < 4) {
            liveKernelQuarterReported = 4;
            wire.add(new dev.jsc.jscomputronics.module.computing.operation.payload.CommandOutputPayload.WireLine(
                    ">>> sys-kernel/gentoo-sources: compiled. Run 'genkernel all' to build the kernel.", ok));
        }

        // Completions: announced live to whoever is looking; with no console open the notice stays queued
        // for the shell to print ahead of the next command instead.
        final java.util.List<net.minecraft.server.level.ServerPlayer> viewers = consoleViewers(level);
        if (!console.settleBuilds(now).isEmpty()) {
            setChanged();
            if (!viewers.isEmpty()) {
                for (final String id : console.drainFinishedBuilds()) {
                    buildQuarterReported.remove(id);
                    wire.add(new dev.jsc.jscomputronics.module.computing.operation.payload.CommandOutputPayload
                            .WireLine(">>> " + buildDisplayName(id) + ": build finished, package installed", ok));
                }
            }
        }
        if (wire.isEmpty() || viewers.isEmpty()) {
            return;
        }
        final var prompt = new dev.jsc.jscomputronics.module.computing.operation.payload.CommandOutputPayload(
                false, "", wire);
        final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.DesktopShellOutputPayload
                .WireLine> desktopWire = new java.util.ArrayList<>();
        for (final var line : wire) {
            desktopWire.add(new dev.jsc.jscomputronics.module.computing.operation.payload.DesktopShellOutputPayload
                    .WireLine(line.text(), line.style()));
        }
        final var desktop = new dev.jsc.jscomputronics.module.computing.operation.payload.DesktopShellOutputPayload(
                false, "", desktopWire);
        for (final net.minecraft.server.level.ServerPlayer viewer : viewers) {
            if (viewer.containerMenu instanceof dev.jsc.jscomputronics.module.computing.menu.DesktopMenu) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, desktop);
            } else {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, prompt);
            }
        }
    }

    /** Every player with this computer's console on screen: its terminal menus, or its open desktop. */
    private java.util.List<net.minecraft.server.level.ServerPlayer> consoleViewers(
            final net.minecraft.server.level.ServerLevel level) {
        final java.util.List<net.minecraft.server.level.ServerPlayer> out = new java.util.ArrayList<>();
        for (final net.minecraft.server.level.ServerPlayer player : level.players()) {
            final boolean viewing = (player.containerMenu
                    instanceof dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu prompt
                    && worldPosition.equals(prompt.hostPos()))
                    || (player.containerMenu instanceof dev.jsc.jscomputronics.module.computing.menu.DesktopMenu desk
                            && worldPosition.equals(desk.hostPos()));
            if (viewing) {
                out.add(player);
            }
        }
        return out;
    }

    private static String buildDisplayName(final String programId) {
        final net.minecraft.resources.ResourceLocation rl =
                net.minecraft.resources.ResourceLocation.tryParse(programId);
        final dev.jsc.jscomputronics.module.computing.os.ProgramSpec spec =
                rl == null ? null : dev.jsc.jscomputronics.module.computing.os.OsRegistry.getProgram(rl);
        return spec != null ? spec.commandName()
                : (programId.contains(":") ? programId.substring(programId.indexOf(':') + 1) : programId);
    }

    /**
     * Whether this computer still has something to run: the installed OS on a disk, or a live-install
     * session whose medium is still in a linked drive. A live session whose medium was pulled out is
     * dropped here (the machine "crashed"), so the next boot lands on the firmware instead of a ghost
     * installer shell.
     */
    public boolean validateOsSession() {
        final dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console = console();
        final dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState live =
                console == null ? null : console.liveInstall();
        if (live != null) {
            if (hasLiveMediumFor(live.distro())) {
                return true;
            }
            console.clearLiveInstall();
            setChanged();
        }
        return installedOsId() != null;
    }

    /** Whether a linked drive still holds the live/source installer medium for {@code distro}. */
    private boolean hasLiveMediumFor(
            final dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState.Distro distro) {
        final net.minecraft.world.level.Level level = getLevel();
        if (level == null) {
            return true; // not resolvable right now; do not kill the session over a missing level
        }
        final String wanted = distro
                == dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState.Distro.ARCH
                ? "arch" : "gentoo";
        for (final long endpoint : linkedEndpoints()) {
            if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                    instanceof dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity reader
                    && reader.insertedKind() == dev.jsc.jscomputronics.module.computing.os.media.MediaKind.OS_INSTALL
                    && reader.insertedPayload() != null
                    && wanted.equals(reader.insertedPayload().getPath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Formats disk slot {@code slot}: erases the installed system, every file, the item storage and the
     * privacy split on it, leaving a blank disk. The boot-order pointer is cleared when it pointed here.
     * Returns whether a disk was actually formatted.
     */
    public boolean formatDisk(final int slot) {
        // Write back through the handler so onContentsChanged fires (setChanged + build invalidation).
        final dev.jsc.jscomputronics.module.computing.os.OsDisks.FormatResult result =
                dev.jsc.jscomputronics.module.computing.os.OsDisks.formatDisk(
                        layout.diskCount(), this::diskInSlot,
                        (stack, s) -> hardware.setStackInSlot(layout.diskStart() + s, stack), slot);
        if (!result.formatted()) {
            return false;
        }
        if (bootDiskSlot == slot) {
            bootDiskSlot = -1;
        }
        if (result == dev.jsc.jscomputronics.module.computing.os.OsDisks.FormatResult.ERASED_SYSTEM) {
            onSystemErased();
        }
        setChanged();
        return true;
    }

    /**
     * A disk carrying a system was just formatted: everything the software layer remembered lived on it,
     * so the console's history, session location and installed-program set go with it. Subclasses hosting
     * software services (the Mainframe) extend this to switch those off too.
     */
    protected void onSystemErased() {
        final dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console = console();
        if (console != null) {
            console.wipeSoftware();
        }
    }

    /**
     * The disk slot the firmware installs onto by default: the first disk without a system (so a second OS
     * lands beside the first for dual boot), else the first disk; {@code -1} when no disk is installed.
     */
    public int defaultInstallSlot() {
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.defaultInstallSlot(
                layout.diskCount(), this::diskInSlot);
    }

    /**
     * Installs {@code osId} onto disk slot {@code preferredSlot}, or ({@code -1}) onto the default target:
     * a slot already carrying this OS (an idempotent re-install), else the first disk without a system, else
     * the first disk. Returns false when the OS is unknown, no disk is present, or the footprint does not fit.
     */
    public boolean installOs(final ResourceLocation osId, final int preferredSlot) {
        // Writing back through setStackInSlot makes onContentsChanged fire (setChanged + build
        // invalidation); the block update then pushes the new disk state to watching clients.
        final boolean installed = dev.jsc.jscomputronics.module.computing.os.OsDisks.installOs(
                layout.diskCount(), this::diskInSlot,
                (stack, s) -> hardware.setStackInSlot(layout.diskStart() + s, stack),
                osId, preferredSlot);
        if (installed && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
        return installed;
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
        tickBuildProgress(level);
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

    /**
     * The disk stack {@link #console} was read from, or null when nothing has been read yet. Identity,
     * not equality: a different stack object means a different physical drive, while writing to the same
     * drive (installing an OS, adding a program) keeps the same object and must NOT discard the state
     * held in memory — doing that resurrected a cleared live-install session from the older disk copy.
     */
    @Nullable
    private ItemStack consoleDisk;

    // Provided here (no @Override: this base does not itself declare ComputerTerminalHost) so the
    // computer subclasses that ARE hosts inherit it and satisfy the interface's console() method.
    public dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console() {
        final ItemStack disk = systemDisk();
        if (consoleDisk != disk) {
            loadConsoleFrom(disk);
        }
        return console;
    }

    /**
     * Reads the console state off {@code disk}, replacing whatever the previous drive left in memory. A
     * disk with no state (a fresh or freshly formatted one) yields an empty console, which is what a
     * clean install must see.
     */
    private void loadConsoleFrom(final ItemStack disk) {
        consoleDisk = disk; // set first: nothing below may recurse back into console()
        console.clear();
        final CompoundTag saved = disk.isEmpty() ? null : disk.get(ComputingModule.DISK_CONSOLE.get());
        if (saved != null) {
            console.load(saved);
        }
    }

    /**
     * Writes the console state back onto the system disk. Called before the block entity is saved and
     * after anything that changes installed software, so the disk is always the record of its own
     * contents.
     */
    @Override
    public void setChanged() {
        // Every mutation of installed software ends in setChanged, so this is the one place that
        // guarantees the disk is current before the player can pull it out. Without it, installing a
        // program and immediately removing the drive would lose the install: the in-memory state is
        // discarded when the slot changes, and the world may not have saved in between.
        flushConsoleToDisk();
        super.setChanged();
    }

    protected void flushConsoleToDisk() {
        // Write back to the drive the state was read from, not to whatever is the system disk now: if a
        // drive has just been swapped, this state belongs to the old one and must not be copied onto it.
        if (consoleDisk == null || consoleDisk.isEmpty()) {
            return;
        }
        final CompoundTag tag = new CompoundTag();
        console.save(tag);
        consoleDisk.set(ComputingModule.DISK_CONSOLE.get(), tag);
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
        bootDiskSlot = tag.contains("BootDisk") ? tag.getInt("BootDisk") : -1;
        computerName = tag.getString("ComputerName");
        if (tag.contains("NodeUuid")) {
            nodeUuid = NodeUuid.fromString(tag.getString("NodeUuid"));
        }
        linkedMonitors.clear();
        for (final long monitor : tag.getLongArray("LinkedMonitors")) {
            linkedMonitors.add(monitor);
        }
        bootedDesktopId = tag.contains("BootedDesktop")
                ? ResourceLocation.tryParse(tag.getString("BootedDesktop")) : null;
        openWindows.clear();
        openWindows.addAll(dev.jsc.jscomputronics.module.computing.os.OpenWindow.loadAll(
                tag.getList("OpenWindows", net.minecraft.nbt.Tag.TAG_COMPOUND)));
        pendingInstallSlot = tag.contains("PendingInstall") ? tag.getInt("PendingInstall") : NO_PENDING_INSTALL;
        // A world saved before the software moved onto the disk still carries the old block-level tag;
        // adopt it once so the machine keeps what it had, and it lands on the disk at the next save.
        if (tag.contains("Console")) {
            console.load(tag.getCompound("Console"));
            consoleDisk = systemDisk(); // adopt it onto the current drive at the next flush
        }
        loadExtra(tag, registries);
        buildDirty = true;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Push the software onto the disk first: the hardware handler below serializes the disk stacks,
        // and a flush after that point would be written to a copy and lost.
        flushConsoleToDisk();
        tag.put(hardwareNbtKey(), hardware.serializeNBT(registries));
        tag.putBoolean("ManualOn", manualOn);
        tag.putBoolean("AutoStart", autoStart);
        if (bootDiskSlot >= 0) {
            tag.putInt("BootDisk", bootDiskSlot);
        }
        if (!computerName.isEmpty()) {
            tag.putString("ComputerName", computerName);
        }
        if (nodeUuid != null) {
            tag.putString("NodeUuid", nodeUuid.asString());
        }
        // The running session survives a reload, exactly like the POST flag: a machine that was left up
        // with a desktop on screen must come back to that desktop, not fall to a shell.
        if (bootedDesktopId != null) {
            tag.putString("BootedDesktop", bootedDesktopId.toString());
        }
        if (!openWindows.isEmpty()) {
            tag.put("OpenWindows", dev.jsc.jscomputronics.module.computing.os.OpenWindow.saveAll(openWindows));
        }
        if (pendingInstallSlot != NO_PENDING_INSTALL) {
            tag.putInt("PendingInstall", pendingInstallSlot);
        }
        if (!linkedMonitors.isEmpty()) {
            tag.putLongArray("LinkedMonitors", linkedMonitors.stream().mapToLong(Long::longValue).toArray());
        }
        // The console rides on the system disk, so flush it there BEFORE the hardware handler is
        // serialized above — otherwise the write would land on a disk stack that was already copied.
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

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getUpdatePacket() {
        // Without this, a mid-session rename (which calls sendBlockUpdated) never reaches the client, so
        // reopening the assembly screen reads a stale, empty name from the client copy of this block entity.
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(final net.minecraft.network.Connection connection,
                             final net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             final HolderLookup.Provider registries) {
        // Apply only the display name from a live block update. The rest of the client state is kept in sync
        // through the menu's ContainerData; running the full loadAdditional here would reset transient fields
        // (power, autostart, linked monitors) to their defaults because the update tag is intentionally minimal.
        final CompoundTag tag = packet.getTag();
        computerName = tag != null ? tag.getString("ComputerName") : "";
    }
}
