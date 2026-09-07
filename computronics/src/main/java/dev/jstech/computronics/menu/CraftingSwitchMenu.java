/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.menu;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.block.CraftingSwitchBlock;
import dev.jstech.computronics.gui.layout.CraftingSwitchLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Menu for the Crafting Switch. It holds no item slots of its own; the face config (name + active) is read
 * from the block entity, which the server keeps in sync via its update tag, and edited back through
 * {@code SetCraftingSwitchFacePayload}. Only the player inventory is wired here, from the shared layout.
 */
public class CraftingSwitchMenu extends AbstractComputerMenu {

    private final ContainerLevelAccess access;
    private final BlockPos switchPos;

    public CraftingSwitchMenu(final int containerId, final Inventory playerInventory,
                              final Level level, final BlockPos switchPos) {
        super(ComputingModule.CRAFTING_SWITCH_MENU.get(), containerId);
        this.access = ContainerLevelAccess.create(level, switchPos);
        this.switchPos = switchPos;
        addPlayerInventory(playerInventory, CraftingSwitchLayout.INV_X, CraftingSwitchLayout.INV_Y);
    }

    public static CraftingSwitchMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                 final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        return new CraftingSwitchMenu(containerId, playerInventory, playerInventory.player.level(), pos);
    }

    public BlockPos switchPos() {
        return switchPos;
    }

    @Override
    public boolean stillValid(final Player player) {
        return access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock() instanceof CraftingSwitchBlock
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        // Only the player inventory holds items here; shift-click just moves between main and hotbar.
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int end = slots.size();
        final int hotbarStart = end - 9;
        if (index < hotbarStart) {
            if (!moveItemStackTo(stack, hotbarStart, end, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, hotbarStart, false)) {
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
