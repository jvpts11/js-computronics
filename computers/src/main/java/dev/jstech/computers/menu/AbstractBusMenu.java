/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.menu;

import dev.jstech.computers.block.DataCableBlock;
import dev.jstech.computers.block.part.AbstractBusPart;
import dev.jstech.computers.gui.layout.BusLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Shared menu for the two bus parts: a single ghost filter slot, the min/max stock steppers, the mode toggle,
 * and the player inventory. Both buses expose an identical configuration surface, so the slot wiring, the
 * stepper buttons, the synced data and the shift-click transfer all live here; the subclasses only register
 * their own {@link MenuType} and the create/fromNetwork factories. The bus name rides in the open packet and
 * is edited back to the server by a dedicated payload, not through the {@link ContainerData} (which is ints).
 */
public abstract class AbstractBusMenu extends AbstractComputerMenu {

    public static final int FILTER_SLOT = 0;

    // Stepper button ids: field + direction + step.
    public static final int BTN_MIN_DOWN1 = 0;
    public static final int BTN_MIN_UP1 = 1;
    public static final int BTN_MIN_DOWN16 = 2;
    public static final int BTN_MIN_UP16 = 3;
    public static final int BTN_MAX_DOWN1 = 4;
    public static final int BTN_MAX_UP1 = 5;
    public static final int BTN_MAX_DOWN16 = 6;
    public static final int BTN_MAX_UP16 = 7;
    public static final int BTN_MODE = 8;

    private final AbstractBusPart part;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos cablePos;
    private final Direction face;
    private String busName;

    protected AbstractBusMenu(final MenuType<?> type, final int containerId, final Inventory playerInventory,
                              final AbstractBusPart part, final Level level, final BlockPos cablePos,
                              final Direction face, final String busName) {
        super(type, containerId);
        this.part = part;
        this.data = part.getDataAccess();
        this.access = ContainerLevelAccess.create(level, cablePos);
        this.cablePos = cablePos;
        this.face = face;
        this.busName = busName == null ? "" : busName;

        addSlot(new SlotItemHandler(part.getFilterHandler(), 0, BusLayout.FILTER_X, BusLayout.FILTER_Y) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return false; // the filter is set by a click, never by dropping an item in
            }

            @Override
            public boolean mayPickup(final Player player) {
                return false;
            }

            @Override
            public boolean isActive() {
                /*
                 * Every bus has a filter: on a crafting bus it pins what the face carries so the engine routes
                 * per face. Only the stock controls (min/max/mode) hide on the passive buses.
                 */
                return filterApplies();
            }
        });
        addPlayerInventory(playerInventory, BusLayout.INV_X, BusLayout.INV_Y);
        addDataSlots(this.data);
    }

    /**
     * Whether this bus exposes a filter. Every bus does: on the autonomous Import/Export buses it selects what
     * to move, and on the passive crafting Input/Receiving buses it pins what the mounted face carries so the
     * crafting engine can route each ingredient (or each output) to the correct face. An empty filter means the
     * face carries anything, matching the raw machine face.
     */
    public boolean filterApplies() {
        return true;
    }

    /**
     * Whether the stock controls (min/max window and continuous/redstone mode) apply to this bus. Only the
     * autonomous Import and Export buses hold stock; the crafting Input and Receiving buses are demand-driven by
     * the crafting engine, so those controls hide and their buttons are refused server-side.
     */
    public boolean stockControlsApply() {
        final var kind = part.type();
        return kind != dev.jstech.computers.block.part.CablePartType.INPUT
                && kind != dev.jstech.computers.block.part.CablePartType.RECEIVING;
    }

    public int min() {
        return data.get(0);
    }

    public int max() {
        return data.get(1);
    }

    public int mode() {
        return data.get(2);
    }

    public boolean linked() {
        return data.get(3) != 0;
    }

    public ItemStack filterStack() {
        return getSlot(FILTER_SLOT).getItem();
    }

    public String busName() {
        return busName;
    }

    public BlockPos cablePos() {
        return cablePos;
    }

    public Direction face() {
        return face;
    }

    /** Updates the locally-known name after the server confirms an edit, so the field stays in sync. */
    public void setBusNameLocal(final String name) {
        this.busName = name == null ? "" : name;
    }

    @Override
    public void clicked(final int slotId, final int button, final ClickType type, final Player player) {
        /*
         * Clicking the filter slot sets it from the carried item (a copy), or clears
         * it with an empty cursor, so the player's item is never consumed.
         */
        if (slotId == FILTER_SLOT) {
            if (filterApplies()) {
                part.setFilter(getCarried());
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (!stockControlsApply()) {
            return false; // passive crafting buses have no stock settings to edit
        }
        switch (id) {
            case BTN_MIN_DOWN1 -> part.adjustMin(-1);
            case BTN_MIN_UP1 -> part.adjustMin(1);
            case BTN_MIN_DOWN16 -> part.adjustMin(-16);
            case BTN_MIN_UP16 -> part.adjustMin(16);
            case BTN_MAX_DOWN1 -> part.adjustMax(-1);
            case BTN_MAX_UP1 -> part.adjustMax(1);
            case BTN_MAX_DOWN16 -> part.adjustMax(-16);
            case BTN_MAX_UP16 -> part.adjustMax(16);
            case BTN_MODE -> part.toggleMode();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(final Player player) {
        return access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock() instanceof DataCableBlock
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        // Only the player inventory holds real items; shift-click just reorganizes it.
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || index == FILTER_SLOT) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int invStart = 1;
        final int invEnd = slots.size();
        // Move between the main inventory and the hotbar.
        final int hotbarStart = invEnd - 9;
        if (index < hotbarStart) {
            if (!moveItemStackTo(stack, hotbarStart, invEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, invStart, hotbarStart, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
