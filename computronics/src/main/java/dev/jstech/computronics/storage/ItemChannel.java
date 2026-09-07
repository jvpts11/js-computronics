/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The item slots of a block face, as a data channel. */
public record ItemChannel(IItemHandler items) implements IDataChannel {

    @Override
    public StorageKey.Kind kind() {
        return StorageKey.Kind.ITEM;
    }

    @Override
    public long insert(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L || !key.isItem()) {
            return 0L;
        }
        // Items insert in vanilla-sized stacks; EXECUTE mutates the handler so each batch sees the remaining
        // room. (Simulation is best-effort — the operation path always executes.)
        final int batch = Math.max(1, key.stack(1).getMaxStackSize());
        long inserted = 0L;
        long remaining = amount;
        while (remaining > 0L) {
            final int chunk = (int) Math.min(remaining, batch);
            final ItemStack leftover = ItemHandlerHelper.insertItem(items, key.stack(chunk), simulate);
            final int accepted = chunk - leftover.getCount();
            if (accepted <= 0) {
                break;
            }
            inserted += accepted;
            remaining -= accepted;
            if (simulate) {
                break; // can't loop a non-mutating simulate; report one batch
            }
        }
        return inserted;
    }

    @Override
    public long extract(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L || !key.isItem()) {
            return 0L;
        }
        long extracted = 0L;
        for (int slot = 0; slot < items.getSlots() && extracted < amount; slot++) {
            final ItemStack inSlot = items.getStackInSlot(slot);
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, key.stack(1))) {
                continue;
            }
            extracted += items.extractItem(slot, (int) Math.min(amount - extracted, Integer.MAX_VALUE), simulate)
                    .getCount();
        }
        return extracted;
    }

    @Override
    public long count(final StorageKey key) {
        if (!key.isItem()) {
            return 0L;
        }
        long total = 0L;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            final ItemStack inSlot = items.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(inSlot, key.stack(1))) {
                total += inSlot.getCount();
            }
        }
        return total;
    }

    @Override
    public List<StorageKey> available() {
        final Set<StorageKey> keys = new LinkedHashSet<>();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            final ItemStack inSlot = items.getStackInSlot(slot);
            if (!inSlot.isEmpty()) {
                keys.add(StorageKey.of(inSlot));
            }
        }
        return new ArrayList<>(keys);
    }
}
