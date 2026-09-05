/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A computer's local storage as a capacity-bounded, component-preserving type → quantity store that lives
 * on its installed disks: each disk's items are its {@link StorageVolume}, so a computer's local storage is
 * literally the union of its disks. A write touches one volume in place and refreshes that disk's usage
 * summary, whatever the disk holds.
 */
public final class LocalStore implements WeightedStore {

    private final List<ItemStack> disks;
    private final Runnable onChanged;
    private final boolean balanced;

    public LocalStore(final List<ItemStack> disks, final Runnable onChanged) {
        this(disks, onChanged, false);
    }

    /**
     * @param balanced when true, writes go to the emptiest disk first instead of filling disks in
     *                 order — what the Load Balancer service buys a server: no single drive fills up
     *                 while its neighbours sit half empty.
     */
    public LocalStore(final List<ItemStack> disks, final Runnable onChanged, final boolean balanced) {
        this.disks = disks;
        this.onChanged = onChanged;
        this.balanced = balanced;
    }

    /** The disks in the order a write should visit them: by free space when balancing, else as given. */
    private List<ItemStack> writeOrder() {
        if (!balanced) {
            return disks;
        }
        final List<ItemStack> ordered = new java.util.ArrayList<>(disks);
        ordered.sort(java.util.Comparator.comparingLong(
                (final ItemStack disk) -> diskCapacity(disk) * StorageKey.MB_EQ_PER_ITEM
                        - DriveVolumes.peek(disk).usedWeight()).reversed());
        return ordered;
    }

    private static long diskCapacity(final ItemStack disk) {
        return disk.getItem() instanceof DiskItem item ? item.spec().capacityItems() : 0L;
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
            total += DriveVolumes.peek(disk).usedWeight();
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
            DriveVolumes.peek(disk).items().forEach((key, count) -> merged.merge(key, count, Long::sum));
        }
        return merged;
    }

    // The owner-local view/insert/extract above is always the full contents — the slider never blocks
    // the owner at their own machine. The public/private split below is a read-only classification the
    // network sees, computed per disk from its public-share permille; it never moves items.

    public int diskCount() {
        return disks.size();
    }

    /** Read-only access to the installed disk stacks, in slot order. */
    public List<ItemStack> disks() {
        return Collections.unmodifiableList(disks);
    }

    /** The public-share permille of one disk (the private default when out of range or not a disk). */
    public int diskPublicPermille(final int index) {
        return index >= 0 && index < disks.size() ? DiskItem.publicPermille(disks.get(index)) : 0;
    }

    /** The used data weight stored on one disk. */
    public long diskUsedWeight(final int index) {
        return index >= 0 && index < disks.size() ? DriveVolumes.peek(disks.get(index)).usedWeight() : 0L;
    }

    /** The capacity data weight of one disk. */
    public long diskCapacityWeight(final int index) {
        return index >= 0 && index < disks.size()
                ? diskCapacity(disks.get(index)) * StorageKey.MB_EQ_PER_ITEM : 0L;
    }

    /** The union of every disk's public view — what the network may read from this computer. */
    public Map<StorageKey, Long> publicView() {
        final Map<StorageKey, Long> merged = new LinkedHashMap<>();
        for (final ItemStack disk : disks) {
            final long capacityWeight = diskCapacity(disk) * StorageKey.MB_EQ_PER_ITEM;
            DiskStorageView.publicView(DriveVolumes.peek(disk).items(), capacityWeight, DiskItem.publicPermille(disk))
                    .forEach((key, count) -> merged.merge(key, count, Long::sum));
        }
        return merged;
    }

    /** The total public weight across every disk. */
    public long publicWeight() {
        long total = 0L;
        for (final ItemStack disk : disks) {
            final long capacityWeight = diskCapacity(disk) * StorageKey.MB_EQ_PER_ITEM;
            total += DiskStorageView.publicWeight(DriveVolumes.peek(disk).items(), capacityWeight,
                    DiskItem.publicPermille(disk));
        }
        return total;
    }

    /** The union of every disk's private view — owner-only, never offered to the network. */
    public Map<StorageKey, Long> privateView() {
        final Map<StorageKey, Long> merged = new LinkedHashMap<>();
        for (final ItemStack disk : disks) {
            final long capacityWeight = diskCapacity(disk) * StorageKey.MB_EQ_PER_ITEM;
            DiskStorageView.privateView(DriveVolumes.peek(disk).items(), capacityWeight, DiskItem.publicPermille(disk))
                    .forEach((key, count) -> merged.merge(key, count, Long::sum));
        }
        return merged;
    }

    /**
     * Extracts up to {@code amount} of {@code key} but only from the public share of each disk, so a network pull can never reach a private item. Used by the network SELECT path; the owner-local {@link #extract} is unaffected.
     */
    public long extractPublic(final StorageKey key, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long taken = 0L;
        for (final ItemStack disk : disks) {
            if (taken >= amount) {
                break;
            }
            final StorageVolume volume = DriveVolumes.peek(disk);
            if (volume.isEmpty()) {
                continue;
            }
            final long capacityWeight = diskCapacity(disk) * StorageKey.MB_EQ_PER_ITEM;
            final long publicHave = DiskStorageView.publicView(volume.items(), capacityWeight,
                    DiskItem.publicPermille(disk)).getOrDefault(key, 0L);
            if (publicHave <= 0L) {
                continue;
            }
            final long got = volume.take(key, Math.min(amount - taken, publicHave));
            if (got > 0L) {
                DriveVolumes.refreshUsage(disk, volume);
                taken += got;
            }
        }
        if (taken > 0L) {
            onChanged.run();
        }
        return taken;
    }

    public long count(final StorageKey key) {
        long total = 0L;
        for (final ItemStack disk : disks) {
            total += DriveVolumes.peek(disk).count(key);
        }
        return total;
    }

    public long insert(final StorageKey key, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        final long unitWeight = key.weight(1L); // 1000 for an item, 1 per mB of fluid
        long remaining = amount;
        for (final ItemStack disk : writeOrder()) {
            if (remaining <= 0L) {
                break;
            }
            final long capacityWeight = diskCapacity(disk) * StorageKey.MB_EQ_PER_ITEM;
            if (capacityWeight <= 0L) {
                continue;
            }
            final StorageVolume volume = DriveVolumes.of(disk);
            final long roomNative = (capacityWeight - volume.usedWeight()) / unitWeight;
            if (roomNative <= 0L) {
                continue;
            }
            final long put = Math.min(remaining, roomNative);
            volume.add(key, put);
            DriveVolumes.refreshUsage(disk, volume);
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
            final StorageVolume volume = DriveVolumes.peek(disk);
            final long got = volume.take(key, amount - taken);
            if (got > 0L) {
                DriveVolumes.refreshUsage(disk, volume);
                taken += got;
            }
        }
        if (taken > 0L) {
            onChanged.run();
        }
        return taken;
    }
}
