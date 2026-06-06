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

    public Map<Item, Long> view() {
        return contents().items();
    }

    public long count(final Item item) {
        return contents().count(item);
    }

    public long insert(final Item item, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        final long room = free();
        if (room <= 0L) {
            return 0L;
        }
        final long stored = Math.min(amount, room);
        final Map<Item, Long> next = new HashMap<>(view());
        next.merge(item, stored, Long::sum);
        write(next);
        return stored;
    }

    public long extract(final Item item, final long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        final long have = count(item);
        final long taken = Math.min(amount, have);
        if (taken <= 0L) {
            return 0L;
        }
        final Map<Item, Long> next = new HashMap<>(view());
        final long left = have - taken;
        if (left <= 0L) {
            next.remove(item);
        } else {
            next.put(item, left);
        }
        write(next);
        return taken;
    }

    private void write(final Map<Item, Long> items) {
        server().set(ComputingModule.SERVER_STORAGE.get(), new ServerStorageContents(items));
        rack.setChanged();
    }
}
