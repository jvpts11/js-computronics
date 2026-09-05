/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A live, capacity-bounded view of one rack unit's storage: the union of the bay drives its chassis
 * claims from the rack's front-panel hotswap slots. The data lives on each drive's own disk
 * components — exactly like a computer's local disks — so pulling a drive takes its data with it,
 * and pulling the server leaves both drives and data in the rack for the next chassis.
 */
public final class ServerStore implements WeightedStore {

    private final ServerRackBlockEntity rack;
    private final int serverSlot;

    public ServerStore(final ServerRackBlockEntity rack, final int serverSlot) {
        this.rack = rack;
        this.serverSlot = serverSlot;
    }

    /** Whether the cabinet behind this store is still in the world; a removed one must read as empty. */
    public boolean isLive() {
        return !rack.isRemoved();
    }

    private LocalStore drives() {
        // A cabinet that left the world this tick still sits in shared per-tick views; it reads as empty
        // rather than serving drives that have already dropped as items.
        final List<ItemStack> claimed = rack.isRemoved() ? List.of() : rack.claimedDriveStacks(serverSlot);
        // With the Load Balancer running, writes spread across the bay's drives instead of filling
        // them in order — the service's whole point.
        return new LocalStore(claimed, () -> {
            // Component writes on the drive stacks never pass through the item handler, so bump the
            // bay's change counter here — this is what lets the NetworkIndex re-read only changed bays.
            rack.markStorageChanged(serverSlot);
            rack.setChanged();
        }, rack.hasService(serverSlot, "load_balancer"));
    }

    public long capacity() {
        final dev.jstech.computronics.rack.RaidMode mode = rack.raidModeOf(serverSlot);
        if (mode == dev.jstech.computronics.rack.RaidMode.NONE) {
            return drives().capacity();
        }
        if (rack.raidFailed(serverSlot)) {
            return 0L; // the array lost more members than its mode tolerates
        }
        // A configured array presents ONE logical volume whose size its mode decides; the drives
        // behind it are members, not separate disks.
        final List<Long> sizes = new ArrayList<>();
        for (final ItemStack drive : rack.claimedDriveStacks(serverSlot)) {
            if (drive.getItem() instanceof dev.jstech.computronics.item.DiskItem disk) {
                sizes.add(disk.spec().capacityItems());
            }
        }
        // A degraded array still presents the volume it promised, so the size is charged against the
        // member count it was formed with rather than what is left in the bay right now.
        return mode.usableCapacity(sizes, rack.raidMemberCount(serverSlot));
    }

    public long capacityWeight() {
        return capacity() * StorageKey.MB_EQ_PER_ITEM;
    }

    public long usedWeight() {
        return drives().usedWeight();
    }

    public long freeWeight() {
        // Bounded by the logical volume, not by the raw drives: a mirror's spare members are
        // redundancy, never extra room.
        return Math.max(0L, Math.min(capacityWeight() - usedWeight(), drives().freeWeight()));
    }

    public long used() {
        return drives().used();
    }

    public long free() {
        return drives().free();
    }

    public Map<StorageKey, Long> view() {
        return drives().view();
    }

    public long count(final StorageKey key) {
        return drives().count(key);
    }

    public long count(final Item item) {
        // Counting by Item aggregates every stored variant of it (named, enchanted, ...), while
        // counting by StorageKey stays variant-exact — callers rely on both behaviors.
        long total = 0L;
        for (final Map.Entry<StorageKey, Long> entry : view().entrySet()) {
            if (entry.getKey().item() == item) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public long insert(final StorageKey key, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        // Never write past the logical volume: with an array configured the usable size is the
        // mode's, so the surplus physical room stays reserved for redundancy.
        final long roomNative = freeWeight() / key.weight(1L);
        return roomNative <= 0L ? 0L : drives().insert(key, Math.min(amount, roomNative));
    }

    public long insert(final Item item, final long amount) {
        return insert(StorageKey.of(item), amount);
    }

    public long extract(final StorageKey key, final long amount) {
        return drives().extract(key, amount);
    }

    public long extract(final Item item, final long amount) {
        return extract(StorageKey.of(item), amount);
    }
}
