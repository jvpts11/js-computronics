/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.network.ServerNode;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A flat, capacity-bounded view of every Server's storage on one network, as a type → quantity model (not 64-per-slot inventories).
 */
public final class NetworkStorage {

    private final List<ServerStore> stores;

    private NetworkStorage(final List<ServerStore> stores) {
        this.stores = stores;
    }

    public static NetworkStorage of(final ServerLevel level, final NetworkUuid network) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<ServerStore> stores = new ArrayList<>();
        for (final ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    stores.add(rack.getServerStorage(loc.slot()));
                }
            });
        }
        return new NetworkStorage(stores);
    }

    public Map<Item, Long> query() {
        final Map<Item, Long> totals = new HashMap<>();
        for (final ServerStore store : stores) {
            store.view().forEach((item, count) -> totals.merge(item, count, Long::sum));
        }
        return totals;
    }

    public long count(final Item item) {
        long total = 0L;
        for (final ServerStore store : stores) {
            total += store.count(item);
        }
        return total;
    }

    public long select(final Item item, final long amount, final IItemHandler destination) {
        final int batchSize = Math.max(1, new ItemStack(item).getMaxStackSize());
        long moved = 0L;
        for (final ServerStore store : stores) {
            long available = store.count(item);
            while (moved < amount && available > 0L) {
                final int batch = (int) Math.min(Math.min(amount - moved, available), batchSize);
                final ItemStack offered = new ItemStack(item, batch);
                final ItemStack leftover = ItemHandlerHelper.insertItem(destination, offered, false);
                final int accepted = batch - leftover.getCount();
                if (accepted <= 0) {
                    return moved; // destination full
                }
                store.extract(item, accepted);
                moved += accepted;
                available -= accepted;
            }
            if (moved >= amount) {
                break;
            }
        }
        return moved;
    }

    public int insert(final ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        final Item item = stack.getItem();
        long remaining = stack.getCount();
        for (final ServerStore store : stores) {
            if (remaining <= 0L) {
                break;
            }
            remaining -= store.insert(item, remaining);
        }
        return (int) (stack.getCount() - remaining);
    }

    public long delete(final Item item, final long amount) {
        long destroyed = 0L;
        for (final ServerStore store : stores) {
            if (destroyed >= amount) {
                break;
            }
            destroyed += store.extract(item, amount - destroyed);
        }
        return destroyed;
    }

    public boolean isEmpty() {
        return stores.isEmpty();
    }
}
