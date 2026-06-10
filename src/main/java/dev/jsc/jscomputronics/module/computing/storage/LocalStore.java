/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A computer's local storage as a capacity-bounded, component-preserving type → quantity store that lives on its installed disks — the data is held in each disk item's {@link ComputingModule#DISK_STORAGE} component, so a computer's local storage is literally the union of its disks.
 */
public final class LocalStore {

    private final List<ItemStack> disks;
    private final Runnable onChanged;

    public LocalStore(final List<ItemStack> disks, final Runnable onChanged) {
        this.disks = disks;
        this.onChanged = onChanged;
    }

    private static long diskCapacity(final ItemStack disk) {
        return disk.getItem() instanceof DiskItem item ? item.spec().capacityItems() : 0L;
    }

    private static ServerStorageContents contentsOf(final ItemStack disk) {
        return disk.getOrDefault(ComputingModule.DISK_STORAGE.get(), ServerStorageContents.EMPTY);
    }

    private static void setContents(final ItemStack disk, final Map<StorageKey, Long> items) {
        disk.set(ComputingModule.DISK_STORAGE.get(), new ServerStorageContents(items));
    }

    public long capacity() {
        long total = 0L;
        for (final ItemStack disk : disks) {
            total += diskCapacity(disk);
        }
        return total;
    }

    public long capacityWeight() {
        return capacity() * StorageKey.MB_EQ_PER_ITEM;
    }

    public long usedWeight() {
        long total = 0L;
        for (final ItemStack disk : disks) {
            total += contentsOf(disk).usedWeight();
        }
        return total;
    }

    public long freeWeight() {
        return Math.max(0L, capacityWeight() - usedWeight());
    }

    public long used() {
        return usedWeight() / StorageKey.MB_EQ_PER_ITEM;
    }

    public long free() {
        return freeWeight() / StorageKey.MB_EQ_PER_ITEM;
    }

    public Map<StorageKey, Long> view() {
        final Map<StorageKey, Long> merged = new LinkedHashMap<>();
        for (final ItemStack disk : disks) {
            contentsOf(disk).items().forEach((key, count) -> merged.merge(key, count, Long::sum));
        }
        return merged;
    }

    public long count(final StorageKey key) {
        long total = 0L;
        for (final ItemStack disk : disks) {
            total += contentsOf(disk).count(key);
        }
        return total;
    }

    public long insert(final StorageKey key, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        final long unitWeight = key.weight(1L); // 1000 for an item, 1 per mB of fluid
        long remaining = amount;
        for (final ItemStack disk : disks) {
            if (remaining <= 0L) {
                break;
            }
            final long roomWeight = diskCapacity(disk) * StorageKey.MB_EQ_PER_ITEM - contentsOf(disk).usedWeight();
            final long roomNative = roomWeight / unitWeight;
            if (roomNative <= 0L) {
                continue;
            }
            final long put = Math.min(remaining, roomNative);
            final Map<StorageKey, Long> next = new HashMap<>(contentsOf(disk).items());
            next.merge(key, put, Long::sum);
            setContents(disk, next);
            remaining -= put;
        }
        final long stored = amount - remaining;
        if (stored > 0L) {
            onChanged.run();
        }
        return stored;
    }

    public long extract(final StorageKey key, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long taken = 0L;
        for (final ItemStack disk : disks) {
            if (taken >= amount) {
                break;
            }
            final ServerStorageContents contents = contentsOf(disk);
            final long have = contents.count(key);
            if (have <= 0L) {
                continue;
            }
            final long take = Math.min(amount - taken, have);
            final Map<StorageKey, Long> next = new HashMap<>(contents.items());
            final long left = have - take;
            if (left <= 0L) {
                next.remove(key);
            } else {
                next.put(key, left);
            }
            setContents(disk, next);
            taken += take;
        }
        if (taken > 0L) {
            onChanged.run();
        }
        return taken;
    }
}
