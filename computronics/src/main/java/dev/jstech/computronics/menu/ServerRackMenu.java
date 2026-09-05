/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.menu;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.gui.layout.ServerRackLayout;
import dev.jstech.computronics.rack.RackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Server Rack: one row per rack unit holding the server slot and the five front-panel
 * hotswap slots that belong to that row (the rack owns them; the mounted chassis decides which are
 * cabled), plus the player inventory. Slot positions come from {@link ServerRackLayout}, the same
 * source the screen draws from.
 */
public class ServerRackMenu extends AbstractComputerMenu {

    private static final int RACK_SLOTS = ServerRackBlockEntity.CAPACITY_U;
    private static final RackLayout LAYOUT = new RackLayout(RACK_SLOTS);
    private static final int FRONT_SLOTS = RACK_SLOTS * RackLayout.SLOTS_PER_U;
    private static final int CONTAINER_SLOTS = RACK_SLOTS + FRONT_SLOTS;

    private final ServerRackBlockEntity rack;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public ServerRackMenu(final int containerId, final Inventory playerInventory,
                          final ServerRackBlockEntity be) {
        super(ComputingModule.SERVER_RACK_MENU.get(), containerId);
        this.rack = be;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.data = be.getDataAccess();

        // Server slot i = rack-unit row i; a taller chassis claims the rows below its slot (the
        // handler rejects them).
        final IItemHandler servers = be.getServers();
        for (int i = 0; i < RACK_SLOTS; i++) {
            addSlot(new SlotItemHandler(servers, i,
                    ServerRackLayout.serverItemX(), ServerRackLayout.itemY(i)));
        }
        // The rack's hotswap slots, row-major: index = row * 5 + column.
        final IItemHandler front = be.getFrontSlots();
        for (int index = 0; index < FRONT_SLOTS; index++) {
            final int row = index / RackLayout.SLOTS_PER_U;
            final int column = index % RackLayout.SLOTS_PER_U;
            addSlot(new SlotItemHandler(front, index,
                    ServerRackLayout.frontItemX(column), ServerRackLayout.itemY(row)));
        }
        addPlayerInventory(playerInventory, ServerRackLayout.INV_X, ServerRackLayout.INV_Y);
        addDataSlots(data);
    }

    public boolean networkLinked() {
        return data.get(ServerRackBlockEntity.DATA_LINKED) != 0;
    }

    /** The cabinet's era, so its screen wears that decade's materials rather than one look for all three. */
    public dev.jstech.core.tier.HardwareEra rackEra() {
        return rack.rackEra();
    }

    /** Whether this cabinet is a compute one, which its screen says instead of calling everything a rack. */
    public dev.jstech.computronics.rack.RackChassis.RackType rackType() {
        return rack.rackType();
    }

    public boolean bayPowerOn(final int slot) {
        return (data.get(ServerRackBlockEntity.DATA_BAY_POWER) & (1 << slot)) != 0;
    }

    /** The cabinet's thermal throttle in percent (100 = running free). */
    public int throttlePercent() {
        final int percent = data.get(ServerRackBlockEntity.DATA_THROTTLE);
        return percent <= 0 ? 100 : percent;
    }

    /** Rebuild progress of the unit's array in permille, or 0 when it is not rebuilding. */
    public int rebuildPermille(final int slot) {
        return slot < 0 || slot >= RACK_SLOTS ? 0
                : data.get(ServerRackBlockEntity.DATA_REBUILD_0 + slot);
    }

    public ItemStack serverInBay(final int i) {
        return i >= 0 && i < RACK_SLOTS ? slots.get(i).getItem() : ItemStack.EMPTY;
    }

    public ItemStack frontSlotStack(final int index) {
        return index >= 0 && index < FRONT_SLOTS ? slots.get(RACK_SLOTS + index).getItem() : ItemStack.EMPTY;
    }

    /**
     * The role of a front slot given the currently mounted chassis. Derived from the menu's own
     * synced slots rather than from the block entity: on the client the rack's handlers are empty
     * (the rack sends no update tag), and only the container's slots carry the real contents.
     */
    public RackLayout.SlotRole frontSlotRole(final int index) {
        return LAYOUT.roleAt(index / RackLayout.SLOTS_PER_U, index % RackLayout.SLOTS_PER_U,
                mountedUnits());
    }

    /** The mounted chassis, read from the synced server slots so the client agrees with the server. */
    public java.util.List<RackLayout.Unit> mountedUnits() {
        final java.util.List<RackLayout.Unit> mounted = new java.util.ArrayList<>();
        for (int i = 0; i < RACK_SLOTS; i++) {
            final dev.jstech.computronics.rack.RackChassis chassis =
                    dev.jstech.computronics.item.ServerItem.chassisOf(serverInBay(i));
            if (chassis != null) {
                mounted.add(new RackLayout.Unit(i, chassis.heightU(),
                        chassis.driveSlots(), chassis.gadgetSlots()));
            }
        }
        return mounted;
    }

    public BlockPos rackPos() {
        return rack.getBlockPos();
    }

    // ---- RAID readout, derived from the synced slots so the client can draw it -------------------

    /** The front-slot indices the unit topped at {@code topRow} claims with the given role. */
    private java.util.List<Integer> claimedSlots(final int topRow, final RackLayout.SlotRole role) {
        final dev.jstech.computronics.rack.RackChassis chassis =
                dev.jstech.computronics.item.ServerItem.chassisOf(serverInBay(topRow));
        final java.util.List<Integer> out = new java.util.ArrayList<>();
        if (chassis == null) {
            return out;
        }
        final java.util.List<RackLayout.Unit> mounted = mountedUnits();
        for (int row = topRow; row < topRow + chassis.heightU() && row < RACK_SLOTS; row++) {
            for (int column = 0; column < RackLayout.SLOTS_PER_U; column++) {
                if (LAYOUT.roleAt(row, column, mounted) == role) {
                    out.add(row * RackLayout.SLOTS_PER_U + column);
                }
            }
        }
        return out;
    }

    /** The RAID mode the unit topped at {@code topRow} runs. */
    public dev.jstech.computronics.rack.RaidMode raidModeAt(final int topRow) {
        for (final int index : claimedSlots(topRow, RackLayout.SlotRole.GADGET)) {
            final ItemStack stack = frontSlotStack(index);
            if (dev.jstech.computronics.item.RackGadgetItem.kindOf(stack)
                    == dev.jstech.computronics.item.RackGadgetItem.Kind.RAID_CONTROLLER) {
                return dev.jstech.computronics.item.RackGadgetItem.raidMode(stack);
            }
        }
        return dev.jstech.computronics.rack.RaidMode.NONE;
    }

    /**
     * A short label for the unit's array state ({@code RAID5}, {@code RAID5 DEGRADED},
     * {@code RAID0 FAILED}), or null when the unit runs no array.
     */
    @org.jetbrains.annotations.Nullable
    public String raidLabel(final int topRow) {
        final dev.jstech.computronics.rack.RaidMode mode = raidModeAt(topRow);
        if (mode == dev.jstech.computronics.rack.RaidMode.NONE) {
            return null;
        }
        int members = 0;
        for (final int index : claimedSlots(topRow, RackLayout.SlotRole.GADGET)) {
            final Integer stored = frontSlotStack(index).get(ComputingModule.RAID_MEMBERS.get());
            if (stored != null) {
                members = stored;
            }
        }
        int present = 0;
        for (final int index : claimedSlots(topRow, RackLayout.SlotRole.DRIVE)) {
            if (frontSlotStack(index).getItem()
                    instanceof dev.jstech.computronics.item.DiskItem) {
                present++;
            }
        }
        if (members > 0 && !mode.survives(members, present)) {
            return mode.name() + " FAILED";
        }
        if (members > present) {
            return mode.name() + " DEGRADED";
        }
        return mode.name();
    }

    @org.jetbrains.annotations.Nullable
    public static ServerRackMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                             final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof ServerRackBlockEntity be) {
            return new ServerRackMenu(containerId, playerInventory, be);
        }
        return null;
    }

    @Override
    public boolean stillValid(final Player player) {
        // The FAMILY, not the one block: the Supercomputer Rack shares this menu, and checking the
        // Server Rack alone would open its GUI for a single tick and then close it.
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock()
                        instanceof dev.jstech.computronics.block.ServerRackBlock
                        && player.canInteractWithBlock(pos, 4.0), true);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMoveBetweenContainerAndPlayer(player, index, CONTAINER_SLOTS);
    }
}
