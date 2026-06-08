/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * An insert-only {@link IItemHandler} adapter over a single Server's {@link ServerStore}, so a timed SELECT can stream items into a chosen destination server (a MOVE within the network) through the standard handler interface.
 */
public final class ServerStoreSink implements IItemHandler {

    private final ServerStore store;

    public ServerStoreSink(final ServerStore store) {
        this.store = store;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final long room = store.free();
        if (room <= 0L) {
            return stack;
        }
        final int accepted = (int) Math.min(stack.getCount(), room);
        if (!simulate && accepted > 0) {
            store.insert(StorageKey.of(stack), accepted);
        }
        return accepted >= stack.getCount() ? ItemStack.EMPTY
                : stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(final int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        return true;
    }
}
