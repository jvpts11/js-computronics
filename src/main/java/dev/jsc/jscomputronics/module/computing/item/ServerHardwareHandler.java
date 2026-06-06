/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * An item-handler view over the hardware of the Server item held in a player's hand, used by the Server assembly GUI.
 */
public final class ServerHardwareHandler implements IItemHandlerModifiable {

    public static final int MOBO = 0;
    public static final int CPU_START = 1;
    public static final int CPU = 4;
    public static final int RAM_START = 5;
    public static final int RAM = 8;
    public static final int GPU_START = 13;
    public static final int GPU = 6;
    public static final int PSU = 19;
    public static final int DISK_START = 20;
    public static final int DISK = 6;
    public static final int SLOTS = 26;

    private final Player player;
    private final InteractionHand hand;

    public ServerHardwareHandler(final Player player, final InteractionHand hand) {
        this.player = player;
        this.hand = hand;
    }

    private ItemStack held() {
        return player.getItemInHand(hand);
    }

    private boolean writable() {
        return held().getItem() instanceof ServerItem;
    }

    private ItemContainerContents contents() {
        return ServerItem.hardware(held());
    }

    private void write(final NonNullList<ItemStack> items) {
        if (!writable()) {
            return; // the Server left the hand (picked up); ignore the write
        }
        held().set(ComputingModule.SERVER_HARDWARE.get(), ItemContainerContents.fromItems(items));
    }

    private NonNullList<ItemStack> snapshot() {
        final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        final ItemContainerContents current = contents();
        for (int i = 0; i < SLOTS && i < current.getSlots(); i++) {
            items.set(i, current.getStackInSlot(i).copy());
        }
        return items;
    }

    @Override
    public int getSlots() {
        return SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        final ItemContainerContents current = contents();
        return slot < current.getSlots() ? current.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(final int slot, final ItemStack stack) {
        final NonNullList<ItemStack> items = snapshot();
        items.set(slot, stack.copyWithCount(Math.min(stack.getCount(), 1)));
        write(items);
    }

    @Override
    public int getSlotLimit(final int slot) {
        return 1; // one component per slot
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        if (slot == MOBO) {
            return stack.getItem() instanceof MotherboardItem;
        }
        if (slot == PSU) {
            return stack.getItem() instanceof PsuItem;
        }
        if (slot >= CPU_START && slot < CPU_START + CPU) {
            return stack.getItem() instanceof CpuItem;
        }
        if (slot >= RAM_START && slot < RAM_START + RAM) {
            return stack.getItem() instanceof RamItem;
        }
        if (slot >= GPU_START && slot < GPU_START + GPU) {
            return stack.getItem() instanceof GpuItem;
        }
        if (slot >= DISK_START && slot < DISK_START + DISK) {
            return stack.getItem() instanceof DiskItem;
        }
        return false;
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack) || !getStackInSlot(slot).isEmpty()) {
            return stack;
        }
        if (!simulate) {
            setStackInSlot(slot, stack.copyWithCount(1));
        }
        return stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        final ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            setStackInSlot(slot, ItemStack.EMPTY);
        }
        return existing.copyWithCount(1);
    }
}
