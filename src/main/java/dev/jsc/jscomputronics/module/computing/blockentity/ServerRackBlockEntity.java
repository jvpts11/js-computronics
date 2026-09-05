/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.common.network.ServerNode;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import dev.jsc.jscomputronics.module.computing.rack.RackChassis;
import dev.jsc.jscomputronics.module.computing.rack.RackLayout;
import dev.jsc.jscomputronics.module.computing.rack.RaidMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Server Rack: a passive container (network Category A, no UUID of its own) measured in rack
 * units. The cabinet offers {@link #CAPACITY_U} rack units; a mounted chassis occupies its height
 * in consecutive rows, addressed by the row of its top edge (server slot i = rack-unit row i, and
 * a taller chassis blocks the rows it covers).
 *
 * <p>The front-panel hotswap slots belong to the RACK, five per rack unit: a mounted chassis
 * cables the slots of the rows it occupies up to its drive and gadget budgets, and drives stay in
 * their slots when the server itself is pulled — the next chassis mounted over those rows inherits
 * them. All server-side storage is therefore held by the bay drives (each drive's own disk
 * components), never by the Server item.
 */
public class ServerRackBlockEntity extends BlockEntity
        implements dev.jsc.jscomputronics.common.peripheral.PeripheralOwnerSupport,
        dev.jsc.jscomputronics.module.computing.os.OsHost,
        dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost,
        software.bernie.geckolib.animatable.GeoBlockEntity {

    // ---- the cabinet as one model: what the renderer needs to know about every row ----

    /** Codes for what a rack unit row holds, as the model names its bones. Row 0 is the bottom U. */
    public static final int UNIT_NONE = 0;
    public static final int UNIT_SERVER_STANDARD = 1;
    public static final int UNIT_SERVER_LEGACY = 2;
    public static final int UNIT_SERVER_VINTAGE = 3;
    public static final int UNIT_STORAGE_2U = 4;
    public static final int UNIT_COMPUTE_2U = 5;
    public static final int UNIT_NODE_2U = 6;
    public static final int UNIT_KVM_SWITCH = 7;
    public static final int UNIT_RACK_UPS = 8;
    public static final int UNIT_COOLING_UNIT = 9;

    /** The model bone names, indexed by unit code. */
    public static final String[] UNIT_BONES = {"", "server_1u_standard", "server_1u_legacy", "server_1u_vintage",
            "storage_2u", "compute_2u", "node_2u", "kvm_switch", "rack_ups", "cooling_unit"};

    private static final software.bernie.geckolib.animation.RawAnimation FANS =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.rack.fans");

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geckoCache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    // What the client copy knows about the cabinet: it holds no inventory, so the server sends the unit
    // code of every row, the bay power mask and the service panel state with each block update.
    private final byte[] clientUnits = new byte[CAPACITY_U];
    private int clientBayPowerOff;
    private boolean clientServicePanelOff;

    /** Supercomputer Rack only: the livery panel taken off, showing the nodes in the cavity. */
    private boolean servicePanelOff;

    @Override
    public void registerControllers(final software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        // The roof fans turn while any bay is powered; the light bar and the seated units are bone
        // visibility set by the renderer, not animation.
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "fans", 0,
                state -> anyBayOn() ? state.setAndContinue(FANS)
                        : software.bernie.geckolib.animation.PlayState.STOP));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geckoCache;
    }

    /** The era of this cabinet, read from its block. */
    public dev.jsc.jscomputronics.common.tier.HardwareEra rackEra() {
        return getBlockState().getBlock()
                instanceof dev.jsc.jscomputronics.module.computing.block.ServerRackBlock rack
                ? rack.era() : dev.jsc.jscomputronics.common.tier.HardwareEra.STANDARD;
    }

    /** What row {@code slot} holds, as a unit code, on either side. */
    public int unitCodeAt(final int slot) {
        if (level != null && level.isClientSide()) {
            return slot >= 0 && slot < CAPACITY_U ? clientUnits[slot] : UNIT_NONE;
        }
        final ItemStack stack = servers.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return UNIT_NONE;
        }
        final RackChassis chassis = ServerItem.chassisOf(stack);
        if (chassis != null) {
            return switch (chassis) {
                case SERVER -> UNIT_SERVER_STANDARD;
                case LEGACY_SERVER -> UNIT_SERVER_LEGACY;
                case VINTAGE_SERVER -> UNIT_SERVER_VINTAGE;
                case STORAGE_SERVER -> UNIT_STORAGE_2U;
                case COMPUTE_SERVER -> UNIT_COMPUTE_2U;
                case SUPERCOMPUTER_NODE -> UNIT_NODE_2U;
            };
        }
        final dev.jsc.jscomputronics.module.computing.item.RackUnitItem.Kind[] kinds =
                dev.jsc.jscomputronics.module.computing.item.RackUnitItem.Kind.values();
        for (final dev.jsc.jscomputronics.module.computing.item.RackUnitItem.Kind kind : kinds) {
            if (dev.jsc.jscomputronics.module.computing.item.RackUnitItem.is(stack, kind)) {
                return switch (kind) {
                    case KVM_SWITCH -> UNIT_KVM_SWITCH;
                    case RACK_UPS -> UNIT_RACK_UPS;
                    case COOLING_UNIT -> UNIT_COOLING_UNIT;
                };
            }
        }
        return UNIT_NONE;
    }

    /** Whether the bay at {@code slot} is switched on, on either side. */
    public boolean bayLit(final int slot) {
        if (level != null && level.isClientSide()) {
            return (clientBayPowerOff & (1 << slot)) == 0;
        }
        return bayPowerOn(slot);
    }

    /** Whether any seated machine's bay is on: what spins the roof fans and lights the bar. */
    public boolean anyBayOn() {
        for (int slot = 0; slot < CAPACITY_U; slot++) {
            if (unitCodeAt(slot) != UNIT_NONE && unitCodeAt(slot) <= UNIT_NODE_2U && bayLit(slot)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any computer at all is seated (the bar glows amber for "seated but dark"). */
    public boolean anyComputerSeated() {
        for (int slot = 0; slot < CAPACITY_U; slot++) {
            final int code = unitCodeAt(slot);
            if (code != UNIT_NONE && code <= UNIT_NODE_2U) {
                return true;
            }
        }
        return false;
    }

    public boolean servicePanelOff() {
        return level != null && level.isClientSide() ? clientServicePanelOff : servicePanelOff;
    }

    /** Takes the supercomputer's livery panel off or puts it back; a server rack has none. */
    public void toggleServicePanel() {
        if (rackType() != RackChassis.RackType.SUPERCOMPUTER) {
            return;
        }
        servicePanelOff = !servicePanelOff;
        setChanged();
        syncVisuals();
    }

    /** Pushes the row contents, the bay power and the panel state to watching clients. */
    public void syncVisuals() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** The whole 2 x 3 x 2 footprint: the renderer draws the cabinet from this block alone. */
    public net.minecraft.world.phys.AABB renderBox() {
        final BlockState state = getBlockState();
        if (!state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            return new net.minecraft.world.phys.AABB(worldPosition);
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(worldPosition);
        for (final BlockPos part : dev.jsc.jscomputronics.module.computing.block.ServerRackStructure.allPositions(
                worldPosition, state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING))) {
            box = box.minmax(new net.minecraft.world.phys.AABB(part));
        }
        return box;
    }

    public static final int CAPACITY_U = 8;

    private final RackLayout layout = new RackLayout(CAPACITY_U);

    private final ItemStackHandler servers = new ItemStackHandler(CAPACITY_U) {
        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            // Servers and rack equipment (KVM, UPS, cooling) bid for the same rack units, but a
            // computer only mounts in the cabinet its chassis belongs to.
            final dev.jsc.jscomputronics.module.computing.rack.MountableRackUnit unit =
                    dev.jsc.jscomputronics.module.computing.rack.MountableRackUnit.of(stack);
            if (unit == null || !acceptsChassis(stack)) {
                return false;
            }
            return layout.canPlace(slot, unit.heightU(), mountedUnitsExcept(slot));
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 1; // each Server is unique
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            if (!simulate) {
                // The software state lives with the item: write the running console back onto the
                // stack before it leaves, so the machine's history travels between racks with it.
                flushConsole(slot);
            }
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(final int slot) {
            // A freshly mounted (or swapped) machine runs POST on its next session; whatever
            // console state the old occupant left in memory dies with the swap.
            unitStates.remove(slot);
            buildCached[slot] = false;
            markStorageChanged(slot);
            setChanged();
            updateBayVisuals();
            syncDisplayEra(); // a swapped machine can be of another era than the one it replaced
        }
    };

    /**
     * The transient software state of the machine mounted at one rack-unit row: its console (loaded
     * from and flushed back to the Server item, so it persists with the item per the design), the
     * POST flag, and the firmware's preferred boot drive. Recreated on every mount.
     */
    private static final class UnitState {
        final dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console =
                new dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState();
        boolean needsPost = true;
        int bootDiskSlot = -1;
        /** The desktop this machine booted into, fixed at POST so later package changes wait for a reboot. */
        @org.jetbrains.annotations.Nullable
        ResourceLocation bootedDesktopId;
        /** The windows open on this machine's desktop; machine state that rides on the Server item. */
        final List<dev.jsc.jscomputronics.module.computing.os.OpenWindow> openWindows = new ArrayList<>();
        /** A guided installer that wrote the system but is still waiting for its reboot. */
        int pendingInstallSlot = dev.jsc.jscomputronics.module.computing.os.OsHost.NO_PENDING_INSTALL;
    }

    private final Map<Integer, UnitState> unitStates = new HashMap<>();

    private UnitState unitState(final int slot) {
        return unitStates.computeIfAbsent(slot, s -> {
            final UnitState state = new UnitState();
            final ItemStack stack = servers.getStackInSlot(s);
            final net.minecraft.nbt.CompoundTag saved =
                    stack.get(ComputingModule.SERVER_CONSOLE.get());
            if (saved != null) {
                state.console.load(saved);
                // Absent keys mean a machine that was never brought up, which still needs POST.
                state.needsPost = !saved.contains("NeedsPost") || saved.getBoolean("NeedsPost");
                state.bootDiskSlot = saved.contains("BootDiskSlot") ? saved.getInt("BootDiskSlot") : -1;
                state.bootedDesktopId = saved.contains("BootedDesktop")
                        ? ResourceLocation.tryParse(saved.getString("BootedDesktop")) : null;
                state.openWindows.addAll(dev.jsc.jscomputronics.module.computing.os.OpenWindow.loadAll(
                        saved.getList("OpenWindows", net.minecraft.nbt.Tag.TAG_COMPOUND)));
                state.pendingInstallSlot = saved.contains("PendingInstall") ? saved.getInt("PendingInstall")
                        : dev.jsc.jscomputronics.module.computing.os.OsHost.NO_PENDING_INSTALL;
            }
            return state;
        });
    }

    /** The console of the machine mounted at {@code slot}, or null when that row holds none. */
    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState consoleOf(final int slot) {
        if (slot < 0 || slot >= CAPACITY_U
                || !(servers.getStackInSlot(slot).getItem() instanceof ServerItem)) {
            return null;
        }
        return unitState(slot).console;
    }

    /**
     * Whether the machine at {@code slot} runs the given server service. Services are what give a
     * server its role: the hardware decides what it can do, the software decides what it does.
     */
    public boolean hasService(final int slot, final String programPath) {
        final dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console = consoleOf(slot);
        return console != null && console.isInstalled("jsc:" + programPath);
    }

    /** Writes the in-memory console of the unit at {@code slot} back onto its Server item. */
    private void flushConsole(final int slot) {
        final UnitState state = unitStates.get(slot);
        final ItemStack stack = servers.getStackInSlot(slot);
        if (state == null || !(stack.getItem() instanceof ServerItem)) {
            return;
        }
        final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        state.console.save(tag);
        // A machine that is already up must still be up after a reload. Keeping the POST flag only in
        // memory made every server re-run POST when the world came back, as if it had been switched off.
        tag.putBoolean("NeedsPost", state.needsPost);
        tag.putInt("BootDiskSlot", state.bootDiskSlot);
        if (state.bootedDesktopId != null) {
            tag.putString("BootedDesktop", state.bootedDesktopId.toString());
        }
        if (!state.openWindows.isEmpty()) {
            tag.put("OpenWindows",
                    dev.jsc.jscomputronics.module.computing.os.OpenWindow.saveAll(state.openWindows));
        }
        if (state.pendingInstallSlot != dev.jsc.jscomputronics.module.computing.os.OsHost.NO_PENDING_INSTALL) {
            tag.putInt("PendingInstall", state.pendingInstallSlot);
        }
        stack.set(ComputingModule.SERVER_CONSOLE.get(), tag);
    }

    private void flushConsoles() {
        for (final int slot : unitStates.keySet()) {
            flushConsole(slot);
        }
    }

    // Five hotswap slots per rack unit, owned by the rack: index = uRow * SLOTS_PER_U + column.
    private final ItemStackHandler frontSlots = new ItemStackHandler(CAPACITY_U * RackLayout.SLOTS_PER_U) {
        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            // Each front slot takes only what its role cables: drives in a drive bay, bay gadgets
            // (RAID controller, cache card) in a gadget bay, nothing in a blocked one.
            return switch (roleOfFrontSlot(slot)) {
                case DRIVE -> stack.getItem() instanceof DiskItem;
                case GADGET -> stack.getItem()
                        instanceof dev.jsc.jscomputronics.module.computing.item.RackGadgetItem;
                case BLOCKED_NO_UNIT, BLOCKED_BUDGET -> false;
            };
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            if (!simulate && getStackInSlot(slot).getItem() instanceof DiskItem) {
                // Pulling a member out of an array is the moment the array's promise is tested:
                // a redundant array keeps its data (and hands back a blank drive), a stripe dies.
                onArrayMemberRemoved(slot);
            }
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 1; // one drive per hotswap slot
        }

        @Override
        protected void onContentsChanged(final int slot) {
            // Storage lives on the drives, so a hotswap is a storage change of the owning unit.
            final RackLayout.Unit unit = RackLayout.unitAt(
                    slot / RackLayout.SLOTS_PER_U, mountedUnits());
            final int top = unit != null ? unit.topU() : slot / RackLayout.SLOTS_PER_U;
            markStorageChanged(top);
            // A replacement drive completing a degraded array starts the rebuild.
            maybeStartRebuild(top);
            setChanged();
        }
    };

    private void updateBayVisuals() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        final BlockState self = getBlockState();
        if (!(self.getBlock() instanceof dev.jsc.jscomputronics.module.computing.block.ServerRackBlock)) {
            return;
        }
        final net.minecraft.core.Direction facing = self.getValue(
                net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        // The cabinet model still shows 4 visual bays over 8 rack units: bay b lights up when
        // either of its two rows carries part of a mounted unit.
        final List<RackLayout.Unit> mounted = mountedUnits();
        for (int h = 0; h < 2; h++) {
            for (int w = 0; w < 2; w++) {
                final int bay = h * 2 + w;
                final boolean occupied = RackLayout.unitAt(bay * 2, mounted) != null
                        || RackLayout.unitAt(bay * 2 + 1, mounted) != null;
                final int bits = occupied ? 3 : 0;
                final BlockPos bayPos = dev.jsc.jscomputronics.module.computing.block.ServerRackStructure
                        .bayBlockPos(worldPosition, facing, w, h);
                final BlockState bayState = serverLevel.getBlockState(bayPos);
                final boolean isRackBlock = bayState.getBlock()
                        instanceof dev.jsc.jscomputronics.module.computing.block.ServerRackBlock
                        || bayState.getBlock()
                        instanceof dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock;
                if (isRackBlock && bayState.getValue(
                        dev.jsc.jscomputronics.module.computing.block.ServerRackBlock.BAYS) != bits) {
                    serverLevel.setBlock(bayPos, bayState.setValue(
                            dev.jsc.jscomputronics.module.computing.block.ServerRackBlock.BAYS, bits),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private final long[] storageModCounts = new long[CAPACITY_U];

    // Each mounted machine's build, parsed from its item once per mount instead of on every tick: the
    // tick, the thermal load and the port count all read it, and a rack of eight was re-parsing eight
    // hardware inventories twenty times a second.
    private final dev.jsc.jscomputronics.common.hardware.ComputerBuild[] buildCache =
            new dev.jsc.jscomputronics.common.hardware.ComputerBuild[CAPACITY_U];
    private final boolean[] buildCached = new boolean[CAPACITY_U];

    // The bay capacity each unit registers, kept until its storage mod count moves (a drive or the machine
    // itself went in or out): the tick asks for it for every unit, every tick, and computing it walks the
    // chassis, the mounted units and the whole front panel.
    private final long[] bayItemsCache = new long[CAPACITY_U];
    private final long[] bayItemsMod = new long[CAPACITY_U];
    private final boolean[] bayItemsCached = new boolean[CAPACITY_U];

    /** The build of the machine mounted at {@code slot} (null for none, or an invalid one), parsed once per mount. */
    @org.jetbrains.annotations.Nullable
    private dev.jsc.jscomputronics.common.hardware.ComputerBuild buildAt(final int slot) {
        if (!buildCached[slot]) {
            buildCache[slot] = ServerItem.build(servers.getStackInSlot(slot));
            buildCached[slot] = true;
        }
        return buildCache[slot];
    }

    public void markStorageChanged(final int slot) {
        if (slot >= 0 && slot < storageModCounts.length) {
            storageModCounts[slot]++;
        }
    }

    public long storageModCount(final int slot) {
        return slot >= 0 && slot < storageModCounts.length ? storageModCounts[slot] : 0L;
    }

    private final Map<UUID, NetworkUuid> registered = new HashMap<>();

    // Per-bay power, stored inverted (bit set = switched OFF) so a fresh rack powers every bay on.
    private int bayPowerOff;

    /** Whether the bay whose unit tops at {@code slot} has its power switch on. */
    public boolean bayPowerOn(final int slot) {
        return slot >= 0 && slot < CAPACITY_U && (bayPowerOff & (1 << slot)) == 0;
    }

    /** Flips the power switch of the bay at {@code slot}; a powered-off machine POSTs on power-up. */
    public void toggleBayPower(final int slot) {
        if (slot < 0 || slot >= CAPACITY_U) {
            return;
        }
        bayPowerOff ^= 1 << slot;
        if (bayPowerOn(slot)) {
            unitState(slot).needsPost = true;
        }
        // Off or a cold start, no desktop survives — the bay switch is this machine's power button,
        // and writing needsPost directly here had let it skip the clearing setNeedsPost does.
        unitState(slot).openWindows.clear();
        unitState(slot).pendingInstallSlot = dev.jsc.jscomputronics.module.computing.os.OsHost.NO_PENDING_INSTALL;
        flushConsole(slot);
        setChanged();
    }

    public static final int DATA_LINKED = 0;
    public static final int DATA_BAY_POWER = 1;
    /** The cabinet's thermal throttle in percent (100 = running free). */
    public static final int DATA_THROTTLE = 2;
    /** Rebuild progress in permille for unit i, at {@code DATA_REBUILD_0 + i}. */
    public static final int DATA_REBUILD_0 = 3;
    public static final int DATA_COUNT = DATA_REBUILD_0 + CAPACITY_U;

    private final net.minecraft.world.inventory.ContainerData data =
            new net.minecraft.world.inventory.SimpleContainerData(DATA_COUNT);

    public ServerRackBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.SERVER_RACK_BE.get(), pos, state);
    }

    public ItemStackHandler getServers() {
        return servers;
    }

    public ItemStackHandler getFrontSlots() {
        return frontSlots;
    }

    public RackLayout layout() {
        return layout;
    }

    public net.minecraft.world.inventory.ContainerData getDataAccess() {
        return data;
    }

    /** The occupancy and front-slot budgets of every mounted chassis. */
    public List<RackLayout.Unit> mountedUnits() {
        return mountedUnitsExcept(-1);
    }

    private List<RackLayout.Unit> mountedUnitsExcept(final int exceptSlot) {
        final List<RackLayout.Unit> mounted = new ArrayList<>();
        for (int i = 0; i < CAPACITY_U; i++) {
            if (i == exceptSlot) {
                continue;
            }
            final RackLayout.Unit unit = dev.jsc.jscomputronics.module.computing.rack
                    .MountableRackUnit.unitAt(servers.getStackInSlot(i), i);
            if (unit != null) {
                mounted.add(unit);
            }
        }
        return mounted;
    }

    /** The role of one front-panel slot given what is currently mounted. */
    public RackLayout.SlotRole roleOfFrontSlot(final int slot) {
        return layout.roleAt(slot / RackLayout.SLOTS_PER_U, slot % RackLayout.SLOTS_PER_U,
                mountedUnits());
    }

    /**
     * The drive stacks the unit mounted at {@code serverSlot} has claimed, in row-major slot order.
     * The returned stacks are the live front-slot stacks: writing their components writes the bay.
     */
    public List<ItemStack> claimedDriveStacks(final int serverSlot) {
        final RackChassis chassis = ServerItem.chassisOf(servers.getStackInSlot(serverSlot));
        if (chassis == null) {
            return List.of();
        }
        final List<RackLayout.Unit> mounted = mountedUnits();
        final List<ItemStack> drives = new ArrayList<>();
        for (int row = serverSlot; row < serverSlot + chassis.heightU() && row < CAPACITY_U; row++) {
            for (int column = 0; column < RackLayout.SLOTS_PER_U; column++) {
                if (layout.roleAt(row, column, mounted) != RackLayout.SlotRole.DRIVE) {
                    continue;
                }
                final ItemStack stack = frontSlots.getStackInSlot(row * RackLayout.SLOTS_PER_U + column);
                if (stack.getItem() instanceof DiskItem) {
                    drives.add(stack);
                }
            }
        }
        return drives;
    }

    /** Inserts a drive into the first free claimed drive slot of the unit at {@code serverSlot}. */
    public boolean insertDrive(final int serverSlot, final ItemStack drive) {
        final RackChassis chassis = ServerItem.chassisOf(servers.getStackInSlot(serverSlot));
        if (chassis == null || !(drive.getItem() instanceof DiskItem)) {
            return false;
        }
        final List<RackLayout.Unit> mounted = mountedUnits();
        for (int row = serverSlot; row < serverSlot + chassis.heightU() && row < CAPACITY_U; row++) {
            for (int column = 0; column < RackLayout.SLOTS_PER_U; column++) {
                if (layout.roleAt(row, column, mounted) != RackLayout.SlotRole.DRIVE) {
                    continue;
                }
                final int slot = row * RackLayout.SLOTS_PER_U + column;
                if (frontSlots.getStackInSlot(slot).isEmpty()) {
                    frontSlots.setStackInSlot(slot, drive.copyWithCount(1));
                    return true;
                }
            }
        }
        return false;
    }

    // ---- RAID: the bay's drives as one logical volume ---------------------------------------------

    /** The front-slot index holding the unit's RAID Controller, or -1 when it has none. */
    public int raidControllerSlot(final int serverSlot) {
        final RackChassis chassis = ServerItem.chassisOf(servers.getStackInSlot(serverSlot));
        if (chassis == null) {
            return -1;
        }
        final List<RackLayout.Unit> mounted = mountedUnits();
        for (int row = serverSlot; row < serverSlot + chassis.heightU() && row < CAPACITY_U; row++) {
            for (int column = 0; column < RackLayout.SLOTS_PER_U; column++) {
                if (layout.roleAt(row, column, mounted) != RackLayout.SlotRole.GADGET) {
                    continue;
                }
                final int index = row * RackLayout.SLOTS_PER_U + column;
                if (dev.jsc.jscomputronics.module.computing.item.RackGadgetItem.kindOf(
                        frontSlots.getStackInSlot(index))
                        == dev.jsc.jscomputronics.module.computing.item.RackGadgetItem.Kind.RAID_CONTROLLER) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** Whether the unit at {@code serverSlot} has a Cache Card in one of its gadget bays. */
    public boolean hasCacheCard(final int serverSlot) {
        final RackChassis chassis = ServerItem.chassisOf(servers.getStackInSlot(serverSlot));
        if (chassis == null) {
            return false;
        }
        final List<RackLayout.Unit> mounted = mountedUnits();
        for (int row = serverSlot; row < serverSlot + chassis.heightU() && row < CAPACITY_U; row++) {
            for (int column = 0; column < RackLayout.SLOTS_PER_U; column++) {
                if (layout.roleAt(row, column, mounted) == RackLayout.SlotRole.GADGET
                        && dev.jsc.jscomputronics.module.computing.item.RackGadgetItem.kindOf(
                                frontSlots.getStackInSlot(row * RackLayout.SLOTS_PER_U + column))
                        == dev.jsc.jscomputronics.module.computing.item.RackGadgetItem.Kind.CACHE_CARD) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The RAID mode the unit at {@code serverSlot} runs, or {@link RaidMode#NONE}. */
    public RaidMode raidModeOf(final int serverSlot) {
        final int controller = raidControllerSlot(serverSlot);
        return controller < 0 ? RaidMode.NONE
                : dev.jsc.jscomputronics.module.computing.item.RackGadgetItem.raidMode(
                        frontSlots.getStackInSlot(controller));
    }

    /**
     * Sets the RAID mode of the unit at {@code serverSlot} and stamps the member count the array
     * forms with, so a later pull reads as a degraded array rather than a smaller one.
     */
    public boolean setRaidMode(final int serverSlot, final RaidMode mode) {
        final int controller = raidControllerSlot(serverSlot);
        if (controller < 0) {
            return false;
        }
        final int drives = claimedDriveStacks(serverSlot).size();
        if (mode != RaidMode.NONE && drives < mode.minDrives()) {
            return false; // not enough drives in the bay to form this array
        }
        final ItemStack stack = frontSlots.getStackInSlot(controller);
        dev.jsc.jscomputronics.module.computing.item.RackGadgetItem.setRaidMode(stack, mode);
        stack.set(ComputingModule.RAID_MEMBERS.get(), mode == RaidMode.NONE ? 0 : drives);
        markStorageChanged(serverSlot);
        setChanged();
        return true;
    }

    /** How many drives the unit's array was formed with (0 when it runs no array). */
    public int raidMemberCount(final int serverSlot) {
        final int controller = raidControllerSlot(serverSlot);
        if (controller < 0) {
            return 0;
        }
        final Integer members = frontSlots.getStackInSlot(controller).get(ComputingModule.RAID_MEMBERS.get());
        return members == null ? 0 : members;
    }

    /** Whether the unit's array is missing members but still serving data. */
    public boolean raidDegraded(final int serverSlot) {
        final RaidMode mode = raidModeOf(serverSlot);
        final int members = raidMemberCount(serverSlot);
        final int present = claimedDriveStacks(serverSlot).size();
        return mode.rebuildable(members, present);
    }

    /** Whether the unit's array has lost more members than its mode tolerates. */
    public boolean raidFailed(final int serverSlot) {
        final RaidMode mode = raidModeOf(serverSlot);
        final int members = raidMemberCount(serverSlot);
        return mode != RaidMode.NONE && members > 0
                && !mode.survives(members, claimedDriveStacks(serverSlot).size());
    }

    /**
     * Tells the network's index that a drive left the bay of the unit topped at {@code topRow}
     * while the machine was running, so the rows it holds for that machine read as unconfirmed
     * until a reindex. This is the maintenance loop the rack's hotswap is meant to create.
     */
    private void notifyHotPull(final int topRow) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // A machine running the Integrity Monitor re-reads its own bay after a hot event, so the
        // index never learns to doubt it. A fragmented index still wants a vacuum by hand.
        if (hasService(topRow, "integrity_monitor")) {
            return;
        }
        final ItemStack stack = servers.getStackInSlot(topRow);
        final UUID node = ServerItem.nodeUuid(stack);
        final NetworkUuid network = node == null ? null : registered.get(node);
        if (network == null) {
            return; // an unnetworked bay has no index to confuse
        }
        final NetworkSystem system = NetworkSystem.get(serverLevel);
        system.mainframePositionOf(network).ifPresent(pos -> {
            if (serverLevel.getBlockEntity(BlockPos.of(pos)) instanceof MainframeBlockEntity mainframe) {
                final String name = ServerItem.customName(stack);
                mainframe.networkIndex().markBayHotPull(new NodeUuid(node),
                        name.isEmpty() ? "srv-" + node.toString().substring(0, 6) : name);
            }
        });
    }

    /**
     * A drive is leaving the front slot {@code frontSlot}. A redundant array keeps the volume: the
     * departing member's share is redistributed across the drives that stay and the drive comes out
     * blank (its stripes were never a standalone copy — nothing is duplicated). A stripe has no
     * redundancy to fall back on, so pulling any member destroys the whole array's contents.
     */
    private void onArrayMemberRemoved(final int frontSlot) {
        final RackLayout.Unit unit = RackLayout.unitAt(frontSlot / RackLayout.SLOTS_PER_U, mountedUnits());
        if (unit == null) {
            return;
        }
        final int top = unit.topU();
        final RaidMode mode = raidModeOf(top);
        if (mode == RaidMode.NONE) {
            // An independent volume travels with its drive, as any computer disk does — and the
            // network index is left holding rows it has not re-read: that is a hot pull.
            notifyHotPull(top);
            return;
        }
        final ItemStack leaving = frontSlots.getStackInSlot(frontSlot);
        if (mode == RaidMode.RAID0) {
            notifyHotPull(top);
            // The stripe is gone: every member (including the one on its way out) is blanked.
            for (final ItemStack drive : claimedDriveStacks(top)) {
                dev.jsc.jscomputronics.module.computing.storage.DriveVolumes.erase(drive);
            }
            markStorageChanged(top);
            setChanged();
            return;
        }
        // A redundant array is now short a member: it waits for a replacement to rebuild onto.
        pendingRebuild.add(top);
        rebuildTicks[top] = 0;
        rebuildTotal[top] = 0;
        final List<ItemStack> survivors = new ArrayList<>();
        for (final ItemStack drive : claimedDriveStacks(top)) {
            if (drive != leaving) {
                survivors.add(drive);
            }
        }
        final dev.jsc.jscomputronics.module.computing.storage.StorageVolume leavingVolume =
                dev.jsc.jscomputronics.module.computing.storage.DriveVolumes.peek(leaving);
        if (!leavingVolume.isEmpty() && !survivors.isEmpty()) {
            final dev.jsc.jscomputronics.module.computing.storage.LocalStore rest =
                    new dev.jsc.jscomputronics.module.computing.storage.LocalStore(survivors, () -> { });
            // Move only what the survivors actually accept, so a rebuild can never mint items.
            for (final var entry : leavingVolume.snapshot().items().entrySet()) {
                final long moved = rest.insert(entry.getKey(), entry.getValue());
                leavingVolume.take(entry.getKey(), moved);
            }
            if (leavingVolume.isEmpty()) {
                dev.jsc.jscomputronics.module.computing.storage.DriveVolumes.erase(leaving);
            } else {
                dev.jsc.jscomputronics.module.computing.storage.DriveVolumes.refreshUsage(leaving, leavingVolume);
            }
        }
        markStorageChanged(top);
        setChanged();
    }

    // ---- Rebuild: putting a replacement member back to work --------------------------------------

    /** Ticks of rebuild work per item of array capacity (a balancing estimate). */
    private static final int REBUILD_TICKS_PER_1K_ITEMS = 20;
    private static final int REBUILD_MIN_TICKS = 40;
    private static final int REBUILD_MAX_TICKS = 20 * 60 * 3; // three minutes on a huge array

    /** Remaining rebuild ticks per unit, and the total each rebuild started with (for the bar). */
    private final int[] rebuildTicks = new int[CAPACITY_U];
    private final int[] rebuildTotal = new int[CAPACITY_U];
    /** Units whose redundant array lost a member and is waiting for a replacement drive. */
    private final Set<Integer> pendingRebuild = new HashSet<>();

    /** Whether the unit's array is currently rebuilding onto a replacement member. */
    public boolean raidRebuilding(final int serverSlot) {
        return serverSlot >= 0 && serverSlot < CAPACITY_U && rebuildTicks[serverSlot] > 0;
    }

    /** Rebuild progress of the unit's array in permille, or 0 when it is not rebuilding. */
    public int raidRebuildPermille(final int serverSlot) {
        if (!raidRebuilding(serverSlot) || rebuildTotal[serverSlot] <= 0) {
            return 0;
        }
        final int done = rebuildTotal[serverSlot] - rebuildTicks[serverSlot];
        return (int) (1000L * done / rebuildTotal[serverSlot]);
    }

    /** Starts a rebuild on the unit if its array just got its missing member back. */
    private void maybeStartRebuild(final int serverSlot) {
        if (!pendingRebuild.contains(serverSlot) || raidRebuilding(serverSlot)) {
            return;
        }
        final RaidMode mode = raidModeOf(serverSlot);
        final int members = raidMemberCount(serverSlot);
        if (mode == RaidMode.NONE || members <= 0
                || claimedDriveStacks(serverSlot).size() < members) {
            return; // still short a member
        }
        final long capacityItems = getServerStorage(serverSlot).capacity();
        final int ticks = (int) Math.max(REBUILD_MIN_TICKS,
                Math.min(REBUILD_MAX_TICKS, capacityItems * REBUILD_TICKS_PER_1K_ITEMS / 1000L));
        rebuildTicks[serverSlot] = ticks;
        rebuildTotal[serverSlot] = ticks;
        pendingRebuild.remove(serverSlot);
        setChanged();
    }

    private void tickRebuilds() {
        for (int slot = 0; slot < CAPACITY_U; slot++) {
            if (rebuildTicks[slot] > 0 && --rebuildTicks[slot] == 0) {
                rebuildTotal[slot] = 0;
                markStorageChanged(slot);
                setChanged();
            }
        }
    }

    /** The bay-backed storage capacity of the unit at {@code serverSlot}, in items: what the network registers. */
    public long bayStorageItems(final int serverSlot) {
        if (bayItemsCached[serverSlot] && bayItemsMod[serverSlot] == storageModCounts[serverSlot]) {
            return bayItemsCache[serverSlot];
        }
        long items = 0L;
        for (final ItemStack drive : claimedDriveStacks(serverSlot)) {
            if (drive.getItem() instanceof DiskItem disk) {
                items += disk.spec().capacityItems();
            }
        }
        bayItemsCache[serverSlot] = items;
        bayItemsMod[serverSlot] = storageModCounts[serverSlot];
        bayItemsCached[serverSlot] = true;
        return items;
    }

    /** The bay-backed storage capacity of the unit at {@code serverSlot}, in megabytes (each drive at its own era). */
    public long bayStorageMb(final int serverSlot) {
        long mb = 0L;
        for (final ItemStack drive : claimedDriveStacks(serverSlot)) {
            if (drive.getItem() instanceof DiskItem disk) {
                mb += disk.spec().capacityMb();
            }
        }
        return mb;
    }

    public dev.jsc.jscomputronics.module.computing.storage.ServerStore getServerStorage(final int slot) {
        return new dev.jsc.jscomputronics.module.computing.storage.ServerStore(this, slot);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final ServerRackBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final NetworkUuid network = adjacentNetwork(level, system);
        data.set(DATA_LINKED, network != null || fabricLinked ? 1 : 0);
        data.set(DATA_BAY_POWER, ~bayPowerOff & 0xFF);
        tickRebuilds();
        for (int slot = 0; slot < CAPACITY_U; slot++) {
            data.set(DATA_REBUILD_0 + slot, raidRebuildPermille(slot));
        }

        final Set<UUID> present = new HashSet<>();
        for (int i = 0; i < CAPACITY_U; i++) {
            final ItemStack stack = servers.getStackInSlot(i);
            if (!(stack.getItem() instanceof ServerItem)) {
                continue;
            }
            // A Server is a node only when it is a valid, powered computer whose bay switch is on.
            final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = buildAt(i);
            if (build == null || !build.isPowered() || !bayPowerOn(i)) {
                continue;
            }
            final UUID node = ensureNodeUuid(stack);
            present.add(node);

            final NetworkUuid previous = registered.get(node);
            if (network == null) {
                // No cable / not networked: the Server is inert, drop its registration.
                if (previous != null) {
                    system.unregisterServer(previous, new NodeUuid(node));
                    registered.remove(node);
                }
                continue;
            }
            if (previous != null && !previous.equals(network)) {
                system.unregisterServer(previous, new NodeUuid(node));
            }
            // Storage capacity comes from the bay drives this unit claims, not from the item.
            system.registerServer(new ServerNode(new NodeUuid(node), network, bayStorageItems(i)),
                    worldPosition.asLong(), i);
            data.set(DATA_THROTTLE, thermalThrottlePercent());
            registered.put(node, network);
        }

        // Unregister Servers that have left the Rack since the last tick.
        registered.entrySet().removeIf(entry -> {
            if (!present.contains(entry.getKey())) {
                system.unregisterServer(entry.getValue(), new NodeUuid(entry.getKey()));
                return true;
            }
            return false;
        });
    }

    private UUID ensureNodeUuid(final ItemStack stack) {
        UUID node = ServerItem.nodeUuid(stack);
        if (node == null) {
            node = UUID.randomUUID();
            stack.set(ComputingModule.SERVER_NODE_UUID.get(), node);
            setChanged();
        }
        return node;
    }

    // A compute cabinet's link state: its fabric reaches an HBW Interface that is on a network. The
    // interface reports it as it surveys the fabric; the cabinet only remembers when and what it said.
    private boolean fabricLinked;
    private long fabricSeenAt = Long.MIN_VALUE;
    private boolean fabricNetworked;

    private NetworkUuid adjacentNetwork(final ServerLevel level, final NetworkSystem system) {
        final Direction facing = getBlockState().getValue(
                net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        final Set<Long> inside = new HashSet<>();
        for (final BlockPos p : dev.jsc.jscomputronics.module.computing.block.ServerRackStructure
                .allPositions(worldPosition, facing)) {
            if (p.equals(worldPosition)) {
                inside.add(p.asLong());
            } else if (level.getBlockEntity(p) instanceof ServerRackPartBlockEntity part
                    && worldPosition.equals(part.controllerPos())) {
                inside.add(p.asLong());
            }
        }
        if (rackType() == RackChassis.RackType.SUPERCOMPUTER) {
            // A compute cabinet sits on the high-compute fabric and answers to nothing else: a data cable
            // on it is ignored, whatever the face. Its nodes are reached through the HBW Interface at the
            // end of the fabric and are never servers on the data network. The link light is what the
            // interface's survey said as it walked the fabric (every tick, any face of the cabinet).
            fabricLinked = fabricNetworked && level.getGameTime() - fabricSeenAt <= 2L;
            return null;
        }
        fabricLinked = false;
        // Cables attach ONLY through the cabinet's rear: the open front is the bay. The high-compute fabric
        // is the supercomputer cabinet's alone, so a server cabinet does not answer to it.
        final Direction back = facing.getOpposite();
        final Set<Long> cables = new HashSet<>();
        for (final long posLong : inside) {
            final BlockPos p = BlockPos.of(posLong);
            final BlockPos neighbor = p.relative(back);
            if (inside.contains(neighbor.asLong())) {
                continue; // a face internal to the cabinet (the front layer's rear)
            }
            if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock cable
                    && cable.tier() != dev.jsc.jscomputronics.common.network.DataTier.HPC) {
                cables.add(neighbor.asLong());
            }
        }
        // Bridge the cable runs this rack touches into one segment, so a Mainframe on one side and a
        // standby on the other are on a single network connected through the rack.
        system.connectivity().bridge(cables);
        for (final long cable : cables) {
            final var net = system.connectivity().networkOf(cable);
            if (net.isPresent()) {
                return net.get();
            }
        }
        return null;
    }

    /**
     * An HBW Interface surveying its fabric found this cabinet on it. Called every tick the cabinet is on
     * a fabric; a cabinet not told for a couple of ticks has come off it.
     */
    public void noteFabricUplink(final long gameTime, final boolean networked) {
        fabricSeenAt = gameTime;
        fabricNetworked = networked;
    }

    public void onBroken(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        registered.forEach((node, network) -> system.unregisterServer(network, new NodeUuid(node)));
        registered.clear();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Unregister the housed Servers on chunk unload too, not just on destruction (the block's
        // onRemove), so they never linger in the still-loaded per-level network. onBroken is idempotent.
        if (level instanceof ServerLevel serverLevel) {
            onBroken(serverLevel);
        }
    }

    // ---- The rack as the mounted computer's face -------------------------------------------------
    //
    // A monitor (or a media reader) cables to the RACK; with exactly one computer mounted, the rack
    // answers the whole OS-host contract by delegating to that machine, so the server runs the same
    // POST / firmware / boot / console pipeline as a desk computer. With two or more computers the
    // monitor cannot address the bays (that takes a KVM switch), so the delegating host goes dark.

    private final Set<Long> linkedPeripherals = new HashSet<>();
    private NodeUuid fallbackNode;

    // ---- Thermal budget: what a dense cabinet costs you ------------------------------------------
    //
    // Every mounted machine dumps its power draw into the cabinet as heat. A rack sheds a fixed
    // amount on its own; past that the machines throttle unless cooling is mounted — which is the
    // trade-off the rack-unit budget is meant to create, since cooling spends the same U a server
    // would. The watt figures are balancing estimates.

    /** Heat a bare cabinet sheds on its own, in watts. */
    private static final int PASSIVE_HEAT_BUDGET_W = 1000;
    /** Heat one Cooling Unit adds to the budget, in watts. */
    private static final int COOLING_UNIT_BUDGET_W = 1500;
    /** However hot it gets, a machine keeps this share of its capacity. */
    private static final int MIN_THROTTLE_PERCENT = 25;

    /** The total power draw of every machine mounted in this rack, in watts. */
    public int thermalLoadWatts() {
        int watts = 0;
        for (int i = 0; i < CAPACITY_U; i++) {
            final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = buildAt(i);
            if (build != null && bayPowerOn(i)) {
                watts += build.powerDraw();
            }
        }
        return watts;
    }

    /** The heat this cabinet can shed: its own dissipation plus every Cooling Unit mounted. */
    public int thermalBudgetWatts() {
        int budget = PASSIVE_HEAT_BUDGET_W;
        for (int i = 0; i < CAPACITY_U; i++) {
            if (dev.jsc.jscomputronics.module.computing.item.RackUnitItem.is(
                    servers.getStackInSlot(i),
                    dev.jsc.jscomputronics.module.computing.item.RackUnitItem.Kind.COOLING_UNIT)) {
                budget += COOLING_UNIT_BUDGET_W;
            }
        }
        return budget;
    }

    /**
     * How much of its capacity a machine in this rack actually delivers, in percent. A cabinet
     * inside its thermal budget runs at full speed; past it every machine throttles by the same
     * proportion, down to a floor — hot hardware slows down, it does not stop.
     */
    public int thermalThrottlePercent() {
        final int load = thermalLoadWatts();
        final int budget = thermalBudgetWatts();
        if (load <= budget || load <= 0) {
            return 100;
        }
        return Math.max(MIN_THROTTLE_PERCENT, (int) (100L * budget / load));
    }

    /** Whether the cabinet is over its thermal budget and throttling the machines in it. */
    public boolean thermalThrottled() {
        return thermalThrottlePercent() < 100;
    }

    /** The kind of cabinet this rack is: which computers it takes. Rack units fit every type. */
    public RackChassis.RackType rackType() {
        // The block decides: one block entity class serves every cabinet type.
        return getBlockState().getBlock()
                instanceof dev.jsc.jscomputronics.module.computing.block.ServerRackBlock rack
                ? rack.rackType() : RackChassis.RackType.SERVER;
    }

    /** Whether this cabinet takes the given stack's chassis (rack equipment always fits). */
    public boolean acceptsChassis(final ItemStack stack) {
        final RackChassis chassis = ServerItem.chassisOf(stack);
        // The cabinet type must match, and a cabinet seats its own era or earlier: a Vintage server
        // still fits a Standard rack, a Standard server never fits a Vintage one.
        return chassis == null
                || (chassis.rackType() == rackType() && chassis.era().isAtMost(rackEra()));
    }

    /** The rows holding computers, in rack order — the channels a KVM switch can address. */
    public List<Integer> computerSlots() {
        final List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < CAPACITY_U; i++) {
            if (servers.getStackInSlot(i).getItem() instanceof ServerItem) {
                slots.add(i);
            }
        }
        return slots;
    }

    /** Whether a KVM Switch is mounted, which is what lets a monitor address more than one machine. */
    public boolean hasKvmSwitch() {
        for (int i = 0; i < CAPACITY_U; i++) {
            if (dev.jsc.jscomputronics.module.computing.item.RackUnitItem.is(
                    servers.getStackInSlot(i),
                    dev.jsc.jscomputronics.module.computing.item.RackUnitItem.Kind.KVM_SWITCH)) {
                return true;
            }
        }
        return false;
    }

    // The bay a linked monitor is currently showing. Only meaningful with a KVM switch mounted;
    // a rack with a single computer always shows that one.
    private int activeChannel;

    /** The channel (rack row) a linked monitor shows, clamped to the machines actually mounted. */
    public int activeChannel() {
        final List<Integer> computers = computerSlots();
        if (computers.isEmpty()) {
            return -1;
        }
        return computers.contains(activeChannel) ? activeChannel : computers.get(0);
    }

    /** Switches the monitor to the machine in {@code slot}; ignored when that row holds none. */
    public void setActiveChannel(final int slot) {
        if (computerSlots().contains(slot)) {
            activeChannel = slot;
            setChanged();
            syncDisplayEra(); // the new channel may be a machine of another era
        }
    }

    /**
     * The row of the machine a linked monitor addresses, or {@code -1} when it cannot address one.
     * One computer needs no extra hardware; two or more need a KVM Switch to pick between them,
     * and without one the monitor has no way to say which machine it means.
     */
    public int soleComputerSlot() {
        if (unitOverride >= 0) {
            return unitOverride; // a caller is addressing one unit directly (see asUnit)
        }
        final List<Integer> computers = computerSlots();
        if (computers.isEmpty()) {
            return -1;
        }
        if (computers.size() == 1) {
            return computers.get(0);
        }
        return hasKvmSwitch() ? activeChannel() : -1;
    }

    // The unit a caller is addressing directly, or -1 when the monitor's channel decides. Every
    // "this rack as a computer" method resolves its unit through soleComputerSlot(), so pointing that
    // one resolver at a row makes all of them act on that row — one code path, no second copy of the
    // machine logic per unit. Scoped, never persisted: the KVM channel is untouched.
    private int unitOverride = -1;

    /** Runs {@code body} with every machine-level method of this rack addressing unit {@code row}. */
    public <T> T asUnit(final int row, final java.util.function.Supplier<T> body) {
        final int previous = unitOverride;
        unitOverride = row;
        try {
            return body.get();
        } finally {
            unitOverride = previous;
        }
    }

    /** The machine seated in {@code row} as a host of its own, independent of the monitor's channel. */
    public dev.jsc.jscomputronics.module.computing.os.OsHost unitHost(final int row) {
        return new RackUnitHost(this, row);
    }

    private ItemStack soleServerStack() {
        final int slot = soleComputerSlot();
        return slot < 0 ? ItemStack.EMPTY : servers.getStackInSlot(slot);
    }

    @org.jetbrains.annotations.Nullable
    private dev.jsc.jscomputronics.common.hardware.ComputerBuild soleBuild() {
        final int slot = soleComputerSlot();
        return slot < 0 ? null : buildAt(slot);
    }

    /** The front-slot index of the {@code i}-th DRIVE-role slot of the unit at {@code topRow}, or -1. */
    private int driveSlotIndex(final int topRow, final int i) {
        final RackChassis chassis = ServerItem.chassisOf(servers.getStackInSlot(topRow));
        if (chassis == null || i < 0) {
            return -1;
        }
        final List<RackLayout.Unit> mounted = mountedUnits();
        int seen = 0;
        for (int row = topRow; row < topRow + chassis.heightU() && row < CAPACITY_U; row++) {
            for (int column = 0; column < RackLayout.SLOTS_PER_U; column++) {
                if (layout.roleAt(row, column, mounted) != RackLayout.SlotRole.DRIVE) {
                    continue;
                }
                if (seen == i) {
                    return row * RackLayout.SLOTS_PER_U + column;
                }
                seen++;
            }
        }
        return -1;
    }

    // OsHost — the machine identity and its bay-backed disks.

    @Override
    public boolean isRunning() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build != null && build.isPowered() && bayPowerOn(soleComputerSlot());
    }

    @Override
    public void setPowered(final boolean on) {
        // A rack machine's power switch is its bay's, so shutting down from inside the system
        // flips the same switch the rack GUI shows.
        final int slot = soleComputerSlot();
        if (slot >= 0 && bayPowerOn(slot) != on) {
            toggleBayPower(slot);
        }
    }

    @Override
    public boolean needsPost() {
        final int slot = soleComputerSlot();
        return slot >= 0 && unitState(slot).needsPost;
    }

    @Override
    public int pendingInstallSlot() {
        final int slot = soleComputerSlot();
        return slot < 0 ? dev.jsc.jscomputronics.module.computing.os.OsHost.NO_PENDING_INSTALL
                : unitState(slot).pendingInstallSlot;
    }

    @Override
    public void setPendingInstallSlot(final int value) {
        final int slot = soleComputerSlot();
        if (slot >= 0) {
            unitState(slot).pendingInstallSlot = value;
            flushConsole(slot); // rides on the Server item, like the rest of the machine's session
            setChanged();
        }
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public ResourceLocation bootedDesktopId() {
        final int slot = soleComputerSlot();
        return slot < 0 ? null : unitState(slot).bootedDesktopId;
    }

    @Override
    public void setBootedDesktopId(@org.jetbrains.annotations.Nullable final ResourceLocation id) {
        final int slot = soleComputerSlot();
        if (slot >= 0) {
            unitState(slot).bootedDesktopId = id;
            flushConsole(slot);
            setChanged();
        }
    }

    @Override
    public List<dev.jsc.jscomputronics.module.computing.os.OpenWindow> openWindows() {
        final int slot = soleComputerSlot();
        return slot < 0 ? List.of() : List.copyOf(unitState(slot).openWindows);
    }

    @Override
    public void setOpenWindows(final List<dev.jsc.jscomputronics.module.computing.os.OpenWindow> windows) {
        final int slot = soleComputerSlot();
        if (slot < 0) {
            return;
        }
        final UnitState state = unitState(slot);
        state.openWindows.clear();
        for (final dev.jsc.jscomputronics.module.computing.os.OpenWindow window : windows) {
            if (state.openWindows.size() >= dev.jsc.jscomputronics.module.computing.os.OpenWindow.MAX) {
                break;
            }
            state.openWindows.add(window);
        }
        flushConsole(slot);
        setChanged();
    }

    @Override
    public void setNeedsPost(final boolean value) {
        final int slot = soleComputerSlot();
        if (slot >= 0) {
            unitState(slot).needsPost = value;
            if (value) {
                unitState(slot).openWindows.clear(); // a restart closes everything
                unitState(slot).pendingInstallSlot = // and is what a finished installer was waiting for
                        dev.jsc.jscomputronics.module.computing.os.OsHost.NO_PENDING_INSTALL;
            }
            // Write it through immediately: the flag lives on the Server item, and a world that unloads
            // before the next save would otherwise forget that this machine had finished booting.
            flushConsole(slot);
            setChanged();
        }
    }

    @Override
    public long ramBuffer() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build == null ? 0L : build.ramBuffer();
    }

    @Override
    public int maxCpuMhz() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        if (build == null) {
            return 0;
        }
        int max = 0;
        for (final dev.jsc.jscomputronics.common.hardware.CpuSpec cpu : build.cpus()) {
            max = Math.max(max, cpu.freqMhz());
        }
        return max;
    }

    @Override
    public int totalVramMb() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build == null ? 0 : build.effectiveVramMb();
    }

    @Override
    public int diskSlots() {
        final RackChassis chassis = ServerItem.chassisOf(soleServerStack());
        return chassis == null ? 0 : chassis.driveSlots();
    }

    @Override
    public ItemStack diskInSlot(final int slot) {
        final int top = soleComputerSlot();
        final int index = top < 0 ? -1 : driveSlotIndex(top, slot);
        return index < 0 ? ItemStack.EMPTY : frontSlots.getStackInSlot(index);
    }

    @Override
    public java.util.List<ItemStack> diskStacks() {
        final List<ItemStack> out = new ArrayList<>(diskSlots());
        for (int i = 0; i < diskSlots(); i++) {
            out.add(diskInSlot(i));
        }
        return out;
    }

    private void setDiskInSlot(final ItemStack stack, final int slot) {
        final int top = soleComputerSlot();
        final int index = top < 0 ? -1 : driveSlotIndex(top, slot);
        if (index >= 0) {
            frontSlots.setStackInSlot(index, stack);
        }
    }

    @Override
    public ItemStack systemDisk() {
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.systemDisk(
                diskSlots(), this::diskInSlot, bootDiskSlot());
    }

    @Override
    public int bootDiskSlot() {
        final int slot = soleComputerSlot();
        return slot < 0 ? -1 : unitState(slot).bootDiskSlot;
    }

    @Override
    public void setBootDiskSlot(final int slot) {
        final int top = soleComputerSlot();
        if (top >= 0) {
            unitState(top).bootDiskSlot = slot;
            setChanged();
        }
    }

    @Override
    public int defaultInstallSlot() {
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.defaultInstallSlot(
                diskSlots(), this::diskInSlot);
    }

    @Override
    public boolean formatDisk(final int slot) {
        final dev.jsc.jscomputronics.module.computing.os.OsDisks.FormatResult result =
                dev.jsc.jscomputronics.module.computing.os.OsDisks.formatDisk(
                        diskSlots(), this::diskInSlot, this::setDiskInSlot, slot);
        if (!result.formatted()) {
            return false;
        }
        final int top = soleComputerSlot();
        if (top >= 0 && unitState(top).bootDiskSlot == slot) {
            unitState(top).bootDiskSlot = -1;
        }
        if (result == dev.jsc.jscomputronics.module.computing.os.OsDisks.FormatResult.ERASED_SYSTEM
                && console() != null) {
            console().wipeSoftware();
        }
        setChanged();
        return true;
    }

    @Override
    public boolean installOs(final ResourceLocation osId) {
        return installOs(osId, -1);
    }

    @Override
    public boolean installOs(final ResourceLocation osId, final int preferredSlot) {
        final boolean installed = dev.jsc.jscomputronics.module.computing.os.OsDisks.installOs(
                diskSlots(), this::diskInSlot, this::setDiskInSlot, osId, preferredSlot);
        if (installed && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        return installed;
    }

    @Override
    public boolean hasOs() {
        return !systemDisk().isEmpty();
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public ResourceLocation installedOsId() {
        final ItemStack disk = systemDisk();
        return disk.isEmpty() ? null : disk.get(ComputingModule.SYSTEM_OS.get());
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.module.computing.os.OsDef installedOs() {
        final ResourceLocation osId = installedOsId();
        return osId == null ? null : dev.jsc.jscomputronics.module.computing.os.OsRegistry.getOs(osId);
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public ResourceLocation installedDesktopId() {
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.installedDesktopId(
                installedOs(), console());
    }

    @Override
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

    /** Whether a media reader cabled to the rack still holds the live installer for {@code distro}. */
    private boolean hasLiveMediumFor(
            final dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState.Distro distro) {
        if (level == null) {
            return true; // not resolvable right now; do not kill the session over a missing level
        }
        final String wanted = distro
                == dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState.Distro.ARCH
                ? "arch" : "gentoo";
        for (final long endpoint : linkedEndpoints()) {
            if (level.getBlockEntity(BlockPos.of(endpoint))
                    instanceof dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity reader
                    && reader.insertedKind() == dev.jsc.jscomputronics.module.computing.os.media.MediaKind.OS_INSTALL
                    && reader.insertedPayload() != null
                    && wanted.equals(reader.insertedPayload().getPath())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console() {
        final int slot = soleComputerSlot();
        return slot < 0 ? null : unitState(slot).console;
    }

    @Override
    public NodeUuid nodeUuid() {
        final ItemStack stack = soleServerStack();
        if (stack.getItem() instanceof ServerItem) {
            return new NodeUuid(ensureNodeUuid(stack));
        }
        if (fallbackNode == null) {
            fallbackNode = NodeUuid.random();
        }
        return fallbackNode;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public NetworkUuid networkUuid() {
        final ItemStack stack = soleServerStack();
        if (!(stack.getItem() instanceof ServerItem)) {
            return null;
        }
        final java.util.UUID node = ServerItem.nodeUuid(stack);
        return node == null ? null : registered.get(node);
    }

    @Override
    public String customName() {
        return ServerItem.customName(soleServerStack());
    }

    @Override
    public void setCustomName(final String name) {
        final ItemStack stack = soleServerStack();
        if (stack.getItem() instanceof ServerItem) {
            ServerItem.setCustomName(stack, name);
            setChanged();
        }
    }

    @Override
    public int installedCpus() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build == null ? 0 : build.cpus().size();
    }

    @Override
    public long systemDiskFreeWeight() {
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.systemDiskFreeWeight(systemDisk());
    }

    @Override
    public long systemDiskFreeMb() {
        final ItemStack disk = systemDisk();
        return dev.jsc.jscomputronics.module.computing.os.OsDisks.systemDiskFreeWeight(disk)
                * diskEra(disk).mbPerItem()
                / dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
    }

    @Override
    public long reservedByOs() {
        // One lookup of the system disk serves both the system and the era the system sits on.
        final ItemStack disk = systemDisk();
        final net.minecraft.resources.ResourceLocation osId =
                disk.isEmpty() ? null : disk.get(ComputingModule.SYSTEM_OS.get());
        final dev.jsc.jscomputronics.module.computing.os.OsDef os =
                osId != null ? dev.jsc.jscomputronics.module.computing.os.OsRegistry.getOs(osId) : null;
        return os != null ? os.footprintItemsOn(diskEra(disk)) : 0L;
    }

    /** The era a disk was made for — what an item and a system cost on it; standard for no disk. */
    private static dev.jsc.jscomputronics.common.tier.HardwareEra diskEra(final ItemStack disk) {
        return disk.getItem() instanceof DiskItem item
                ? item.spec().era() : dev.jsc.jscomputronics.common.tier.HardwareEra.STANDARD;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.common.tier.HardwareEra installedEra() {
        final int mobo = dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.MOBO;
        final net.minecraft.world.item.component.ItemContainerContents parts =
                ServerItem.hardware(soleServerStack());
        // A server's hardware is a data component, so its container is only as long as what was written
        // to it: an empty one has no slots at all and rejects even index 0. Never index it blindly.
        if (mobo >= parts.getSlots()) {
            return null;
        }
        return parts.getStackInSlot(mobo).getItem()
                instanceof dev.jsc.jscomputronics.module.computing.item.MotherboardItem m
                ? m.spec().era() : null;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.common.tier.HardwareEra displayEra() {
        // The client copy of a rack holds no mounted servers, so the era has to travel to it: screens
        // that dress themselves by era (the boot sequence, the terminal bezel) render client-side.
        if (level != null && level.isClientSide()) {
            return clientEra;
        }
        return installedEra();
    }

    // ComputerTerminalHost — the physical terminal's monitoring surface over the same machine.

    @Override
    public boolean computerRunning() {
        return isRunning();
    }

    @Override
    public boolean computerBuildValid() {
        return isRunning();
    }

    @Override
    public int networkLinkState() {
        return networkUuid() != null ? 1 : 0; // a rack server reads its network from the rack cable
    }

    @Override
    public long orchestrationCapacity() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build == null ? 0L : throttled(build.totalCapacity());
    }

    /** Applies the cabinet's thermal throttle to a machine's capacity. */
    public long throttled(final long capacity) {
        final int percent = thermalThrottlePercent();
        return percent >= 100 ? capacity : capacity * percent / 100;
    }

    @Override
    public int computerQueues() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build == null || !build.isPowered() ? 0 : build.parallelQueues();
    }

    @Override
    public long computerRamBuffer() {
        return ramBuffer();
    }

    @Override
    public int networkServerCount() {
        final NetworkUuid network = networkUuid();
        if (network == null || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        return NetworkSystem.get(serverLevel).serversOf(network).size();
    }

    @Override
    public int cpuSlots() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        final RackChassis chassis = ServerItem.chassisOf(soleServerStack());
        return build == null || chassis == null ? 0
                : Math.min(build.motherboard().cpuSlots(), chassis.maxCpus());
    }

    @Override
    public int installedRam() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build == null ? 0 : build.rams().size();
    }

    @Override
    public int ramSlots() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build == null ? 0 : build.motherboard().ramSlots();
    }

    @Override
    public int installedGpus() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        return build == null ? 0 : build.gpus().size();
    }

    @Override
    public int gpuSlots() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = soleBuild();
        final RackChassis chassis = ServerItem.chassisOf(soleServerStack());
        return build == null || chassis == null ? 0
                : Math.min(build.motherboard().pcieSlots(), chassis.maxPcie());
    }

    @Override
    public int installedDisks() {
        int count = 0;
        for (final ItemStack disk : diskStacks()) {
            if (disk.getItem() instanceof DiskItem) {
                count++;
            }
        }
        return count;
    }

    @Override
    public dev.jsc.jscomputronics.module.computing.storage.LocalStore localStore() {
        final int slot = Math.max(0, soleComputerSlot());
        return new dev.jsc.jscomputronics.module.computing.storage.LocalStore(
                claimedDriveStacks(slot), () -> {
                    markStorageChanged(slot);
                    setChanged();
                });
    }

    private long netStorageItems() {
        long capacity = 0L;
        for (final ItemStack disk : diskStacks()) {
            if (disk.getItem() instanceof DiskItem item) {
                capacity += item.spec().capacityItems();
            }
        }
        return Math.max(0L, capacity - reservedByOs());
    }

    @Override
    public long localStorageUsed() {
        return localStore().used();
    }

    @Override
    public long localStorageCapacity() {
        return netStorageItems();
    }

    @Override
    public dev.jsc.jscomputronics.module.computing.storage.DataSink localStorage() {
        return new dev.jsc.jscomputronics.module.computing.storage.StoreSink(localStore());
    }

    @Override
    public int usableStorageSlots() {
        final long capacity = netStorageItems();
        return capacity <= 0 ? 0 : (int) Math.min(27, (capacity + 63) / 64);
    }

    @Override
    public boolean isMainframeHost() {
        return false;
    }

    // PeripheralOwnerSupport — monitors and media readers cable to the rack itself.

    @Override
    public Set<Long> peripheralEndpoints() {
        return linkedPeripherals;
    }

    @Override
    public void markPeripheralChange() {
        setChanged();
    }

    @Override
    public int maxEndpoints() {
        // Cabling a monitor to the cabinet only needs SOME machine with ports — which one the screen
        // ends up showing is the KVM's business, decided when the player uses the monitor. Reading
        // the active machine here would refuse the cable outright on a rack that merely lacks a
        // switch, and blame the hardware for it.
        int ports = 0;
        for (final int slot : computerSlots()) {
            final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = buildAt(slot);
            if (build != null) {
                ports = Math.max(ports, build.motherboard().peripheralPorts());
            }
        }
        return ports;
    }

    @Override
    public Set<Long> occupiedPositions(final long ownerPos) {
        // A peripheral cable may touch any block of the 2x2x? cabinet, not just the controller.
        final net.minecraft.world.level.block.state.BlockState state = getBlockState();
        if (!state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            return Set.of(ownerPos);
        }
        final Direction facing = state.getValue(
                net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        final Set<Long> positions = new HashSet<>();
        for (final BlockPos p : dev.jsc.jscomputronics.module.computing.block.ServerRackStructure
                .allPositions(BlockPos.of(ownerPos), facing)) {
            positions.add(p.asLong());
        }
        return positions;
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        servers.deserializeNBT(registries, tag.getCompound("Servers"));
        resizeAfterLoad(servers, CAPACITY_U);
        frontSlots.deserializeNBT(registries, tag.getCompound("FrontSlots"));
        resizeAfterLoad(frontSlots, CAPACITY_U * RackLayout.SLOTS_PER_U);
        linkedPeripherals.clear();
        for (final long endpoint : tag.getLongArray("Peripherals")) {
            linkedPeripherals.add(endpoint);
        }
        bayPowerOff = tag.getInt("BayPowerOff");
        servicePanelOff = tag.getBoolean("ServicePanelOff");
        // A rebuild in flight survives a reload, as does an array still waiting for its replacement.
        final int[] savedTicks = tag.getIntArray("RebuildTicks");
        final int[] savedTotal = tag.getIntArray("RebuildTotal");
        for (int i = 0; i < CAPACITY_U; i++) {
            rebuildTicks[i] = i < savedTicks.length ? savedTicks[i] : 0;
            rebuildTotal[i] = i < savedTotal.length ? savedTotal[i] : 0;
        }
        pendingRebuild.clear();
        for (final int slot : tag.getIntArray("PendingRebuild")) {
            pendingRebuild.add(slot);
        }
    }

    // ItemStackHandler.deserializeNBT resizes the handler to the persisted Size, so a rack saved
    // under the old 4-bay layout would come back too small and out-of-range the rack-unit loops.
    // Re-expand it, keeping whatever fits (this is a size fixup, not a data migration).
    private static void resizeAfterLoad(final ItemStackHandler handler, final int size) {
        if (handler.getSlots() == size) {
            return;
        }
        final NonNullList<ItemStack> kept = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(handler.getSlots(), size); i++) {
            kept.set(i, handler.getStackInSlot(i));
        }
        handler.setSize(size);
        for (int i = 0; i < size; i++) {
            handler.setStackInSlot(i, kept.get(i));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Console state persists WITH each Server item, so flush the live sessions onto their
        // stacks before the stacks themselves are serialized.
        flushConsoles();
        tag.put("Servers", servers.serializeNBT(registries));
        tag.put("FrontSlots", frontSlots.serializeNBT(registries));
        tag.putLongArray("Peripherals", new ArrayList<>(linkedPeripherals));
        tag.putInt("BayPowerOff", bayPowerOff);
        tag.putBoolean("ServicePanelOff", servicePanelOff);
        tag.putIntArray("RebuildTicks", rebuildTicks.clone());
        tag.putIntArray("RebuildTotal", rebuildTotal.clone());
        tag.putIntArray("PendingRebuild", new ArrayList<>(pendingRebuild));
    }

    // The era of the machine the monitor is currently showing, as last received from the server. Only
    // ever written on the client; the server always answers from the mounted hardware itself.
    @org.jetbrains.annotations.Nullable
    private dev.jsc.jscomputronics.common.tier.HardwareEra clientEra;

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        // Only the active channel's era travels. Sending the mounted stacks would put every server's
        // full build on the wire on every block update, for one enum the screens need.
        final dev.jsc.jscomputronics.common.tier.HardwareEra era = installedEra();
        tag.putInt("DisplayEra", era == null ? -1 : era.ordinal());
        // The cabinet model: one byte per row says what is seated there, the mask says which bays are
        // off, and the panel flag whether the supercomputer's livery is on. Enough to draw it all.
        final byte[] units = new byte[CAPACITY_U];
        for (int slot = 0; slot < CAPACITY_U; slot++) {
            units[slot] = (byte) unitCodeAt(slot);
        }
        tag.putByteArray("Units", units);
        tag.putInt("BayPowerOff", bayPowerOff);
        tag.putBoolean("ServicePanelOff", servicePanelOff);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(final net.minecraft.network.Connection connection,
                             final net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             final HolderLookup.Provider registries) {
        // Apply only the era. Running the full loadAdditional here would deserialize empty inventories
        // over the client copy and reset the transient rack state to its defaults.
        final CompoundTag tag = packet.getTag();
        final int ordinal = tag != null ? tag.getInt("DisplayEra") : -1;
        final dev.jsc.jscomputronics.common.tier.HardwareEra[] eras =
                dev.jsc.jscomputronics.common.tier.HardwareEra.values();
        clientEra = ordinal >= 0 && ordinal < eras.length ? eras[ordinal] : null;
        if (tag != null) {
            applyVisualTag(tag);
        }
    }

    @Override
    public void handleUpdateTag(final CompoundTag tag, final HolderLookup.Provider registries) {
        // The chunk-load path: the same visual state the block-update path carries.
        super.handleUpdateTag(tag, registries);
        applyVisualTag(tag);
    }

    private void applyVisualTag(final CompoundTag tag) {
        final byte[] units = tag.getByteArray("Units");
        java.util.Arrays.fill(clientUnits, (byte) UNIT_NONE);
        System.arraycopy(units, 0, clientUnits, 0, Math.min(units.length, clientUnits.length));
        clientBayPowerOff = tag.getInt("BayPowerOff");
        clientServicePanelOff = tag.getBoolean("ServicePanelOff");
    }

    /** Pushes the active channel's era to watching clients, so era-dressed screens match the machine. */
    private void syncDisplayEra() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }
}
