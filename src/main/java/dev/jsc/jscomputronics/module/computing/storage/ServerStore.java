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
public final class ServerStore {

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

    public long used() {
        return contents().total();
    }

    public long free() {
        return Math.max(0L, capacity() - used());
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
        final long room = free();
        if (room <= 0L) {
            return 0L;
        }
        final long stored = Math.min(amount, room);
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
        rack.setChanged();
    }
}
