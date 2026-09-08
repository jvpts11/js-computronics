/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Base for the computing menus, holding the two pieces every one of them repeated: the standard 3x9 + hotbar player-inventory layout, and the standard shift-click transfer between a block's container slots and the player's inventory. Display-only menus that carry no player inventory simply do not call these.
 */
public abstract class AbstractComputerMenu extends AbstractContainerMenu {

    /** The vertical gap between the inventory's top row and the hotbar, in the vanilla layout. */
    private static final int HOTBAR_GAP = 58;

    protected AbstractComputerMenu(final MenuType<?> type, final int containerId) {
        super(type, containerId);
    }

    /**
     * Lays out the player's 27 inventory slots in a 3x9 grid whose top-left is at {@code (x, invY)}, with the 9 hotbar slots one standard gap below. Every computing GUI uses this exact grid; only its position differs.
     */
    protected void addPlayerInventory(final Inventory inventory, final int x, final int invY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, x + col * 18, invY + HOTBAR_GAP));
        }
    }

    /**
     * The standard shift-click move for a menu whose first {@code containerSlots} slots belong to the block and the rest are the player's inventory: a click in the block area pushes the stack to the player, a click in the player area pulls it into the block. Returns the moved prototype, or empty when nothing moved (the contract {@code quickMoveStack} expects).
     */
    protected ItemStack quickMoveBetweenContainerAndPlayer(final Player player, final int index,
                                                           final int containerSlots) {
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int total = slots.size();
        if (index < containerSlots) {
            if (!moveItemStackTo(stack, containerSlots, total, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, containerSlots, false)) {
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
