/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * A live, capacity-bounded view of one Server's storage, housed in a Server Rack.
 */
public final class ServerStore implements WeightedStore {

    private final ServerRackBlockEntity rack;
    private final int serverSlot;

    public ServerStore(final ServerRackBlockEntity rack, final int serverSlot) {
        this.rack = rack;
        this.serverSlot = serverSlot;
    }

    private ItemStack server() {
        return rack.getServers().getStackInSlot(serverSlot);
    }

    private ServerStorageContents contents() {
        return ServerItem.storage(server());
    }

    public long capacity() {
        final ComputerBuild build = ServerItem.build(server());
        return build == null ? 0L : build.totalStorageItems();
    }

    public long capacityWeight() {
        return capacity() * StorageKey.MB_EQ_PER_ITEM;
    }

    public long usedWeight() {
        return contents().usedWeight();
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
        return contents().items();
    }

    public long count(final StorageKey key) {
        return contents().count(key);
    }

    public long count(final Item item) {
        return contents().count(item);
    }

    public long insert(final StorageKey key, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        // Room in native units = free data weight / this type's weight-per-unit (1000 for an item,
        // 1 for a mB of fluid), so a disk holds any mix bounded by the same capacity.
        final long roomNative = freeWeight() / key.weight(1L);
        if (roomNative <= 0L) {
            return 0L;
        }
        final long stored = Math.min(amount, roomNative);
        final Map<StorageKey, Long> next = new HashMap<>(view());
        next.merge(key, stored, Long::sum);
        write(next);
        return stored;
    }

    public long insert(final Item item, final long amount) {
        return insert(StorageKey.of(item), amount);
    }

    public long extract(final StorageKey key, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        final long have = count(key);
        final long taken = Math.min(amount, have);
        if (taken <= 0L) {
            return 0L;
        }
        final Map<StorageKey, Long> next = new HashMap<>(view());
        final long left = have - taken;
        if (left <= 0L) {
            next.remove(key);
        } else {
            next.put(key, left);
        }
        write(next);
        return taken;
    }

    public long extract(final Item item, final long amount) {
        return extract(StorageKey.of(item), amount);
    }

    private void write(final Map<StorageKey, Long> items) {
        server().set(ComputingModule.SERVER_STORAGE.get(), new ServerStorageContents(items));
        // An in-place component write never passes through the item handler, so bump the bay's
        // change counter here — this is what lets the NetworkIndex re-read only changed bays.
        rack.markStorageChanged(serverSlot);
        rack.setChanged();
    }
}
