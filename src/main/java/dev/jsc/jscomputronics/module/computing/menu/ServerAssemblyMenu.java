/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for assembling a Server: hardware slots (board, CPU, RAM, GPU, PSU, disks) over the held Server item's hardware, plus the player inventory.
 */
public class ServerAssemblyMenu extends AbstractContainerMenu {

    private static final int HARDWARE_SLOTS = ServerHardwareHandler.SLOTS;

    private final Player owner;
    private final InteractionHand hand;

    public ServerAssemblyMenu(final int containerId, final Inventory playerInventory,
                              final InteractionHand hand) {
        super(ComputingModule.SERVER_ASSEMBLY_MENU.get(), containerId);
        this.owner = playerInventory.player;
        this.hand = hand;
        final ServerHardwareHandler hw = new ServerHardwareHandler(owner, hand);

        // Left column: board + PSU on one row, disks in a 2-wide grid below.
        addSlot(new SlotItemHandler(hw, ServerHardwareHandler.MOBO, 8, 28));
        addSlot(new SlotItemHandler(hw, ServerHardwareHandler.PSU, 26, 28));
        for (int i = 0; i < ServerHardwareHandler.DISK; i++) {
            addSlot(new SlotItemHandler(hw, ServerHardwareHandler.DISK_START + i,
                    8 + (i % 2) * 18, 58 + (i / 2) * 18));
        }
        // Middle column: CPUs on a row, RAM in 2 rows, GPUs in 2 rows.
        for (int i = 0; i < ServerHardwareHandler.CPU; i++) {
            addSlot(new SlotItemHandler(hw, ServerHardwareHandler.CPU_START + i, 52 + i * 18, 28));
        }
        for (int i = 0; i < ServerHardwareHandler.RAM; i++) {
            addSlot(new SlotItemHandler(hw, ServerHardwareHandler.RAM_START + i,
                    52 + (i % 4) * 18, 58 + (i / 4) * 18));
        }
        for (int i = 0; i < ServerHardwareHandler.GPU; i++) {
            addSlot(new SlotItemHandler(hw, ServerHardwareHandler.GPU_START + i,
                    52 + (i % 3) * 18, 106 + (i / 3) * 18));
        }

        addPlayerInventory(playerInventory);
    }

    public static ServerAssemblyMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                 final RegistryFriendlyByteBuf buf) {
        return new ServerAssemblyMenu(containerId, playerInventory, buf.readEnum(InteractionHand.class));
    }

    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.common.hardware.ComputerBuild currentBuild() {
        final java.util.List<ItemStack> parts = new java.util.ArrayList<>(HARDWARE_SLOTS);
        for (int i = 0; i < HARDWARE_SLOTS; i++) {
            parts.add(slots.get(i).getItem());
        }
        return ServerItem.buildFrom(parts);
    }

    private void addPlayerInventory(final Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 160 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 218));
        }
    }

    @Override
    public boolean stillValid(final Player player) {
        // Valid only while the player is still holding a Server in that hand.
        return owner == player && owner.getItemInHand(hand).getItem() instanceof ServerItem;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int total = slots.size();

        if (index < HARDWARE_SLOTS) {
            if (!moveItemStackTo(stack, HARDWARE_SLOTS, total, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, HARDWARE_SLOTS, false)) {
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
