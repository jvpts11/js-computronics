/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * An {@link IItemHandlerModifiable} view over the storage of a single Server housed in a Server Rack.
 */
public final class ServerStorageHandler implements IItemHandlerModifiable {

    public static final int SLOTS = 27;

    private final ServerRackBlockEntity rack;
    private final int serverSlot;

    public ServerStorageHandler(final ServerRackBlockEntity rack, final int serverSlot) {
        this.rack = rack;
        this.serverSlot = serverSlot;
    }

    private ItemStack server() {
        return rack.getServers().getStackInSlot(serverSlot);
    }

    private ItemContainerContents contents() {
        return ServerItem.storage(server());
    }

    private void write(final NonNullList<ItemStack> items) {
        final ItemStack server = server();
        server.set(ComputingModule.SERVER_STORAGE.get(), ItemContainerContents.fromItems(items));
        rack.setChanged();
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
        items.set(slot, stack);
        write(items);
    }

    @Override
    public int getSlotLimit(final int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack existing = getStackInSlot(slot);
        final int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }
            final int room = limit - existing.getCount();
            if (room <= 0) {
                return stack;
            }
            final int moved = Math.min(room, stack.getCount());
            if (!simulate) {
                final ItemStack merged = existing.copy();
                merged.grow(moved);
                setStackInSlot(slot, merged);
            }
            return stack.getCount() > moved ? stack.copyWithCount(stack.getCount() - moved) : ItemStack.EMPTY;
        }
        final int moved = Math.min(limit, stack.getCount());
        if (!simulate) {
            setStackInSlot(slot, stack.copyWithCount(moved));
        }
        return stack.getCount() > moved ? stack.copyWithCount(stack.getCount() - moved) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        final ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final int taken = Math.min(amount, existing.getCount());
        if (!simulate) {
            final ItemStack remaining = existing.getCount() > taken
                    ? existing.copyWithCount(existing.getCount() - taken) : ItemStack.EMPTY;
            setStackInSlot(slot, remaining);
        }
        return existing.copyWithCount(taken);
    }
}
