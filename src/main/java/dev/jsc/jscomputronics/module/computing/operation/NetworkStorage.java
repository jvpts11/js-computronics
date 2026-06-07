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
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A flat, capacity-bounded view of every Server's storage on one network, as a type → quantity model (not 64-per-slot inventories).
 */
public final class NetworkStorage {

    /**
     * One Server's store paired with the node identity that selects it for filtering.
     */
    private record Entry(NodeUuid node, ServerStore store) {
    }

    private final List<Entry> entries;

    private NetworkStorage(final List<Entry> entries) {
        this.entries = entries;
    }

    public static NetworkStorage of(final ServerLevel level, final NetworkUuid network) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<Entry> entries = new ArrayList<>();
        for (final ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    entries.add(new Entry(server.nodeUuid(), rack.getServerStorage(loc.slot())));
                }
            });
        }
        return new NetworkStorage(entries);
    }

    public Map<Item, Long> query() {
        final Map<Item, Long> totals = new HashMap<>();
        for (final Entry entry : entries) {
            entry.store().view().forEach((item, count) -> totals.merge(item, count, Long::sum));
        }
        return totals;
    }

    public long count(final Item item) {
        long total = 0L;
        for (final Entry entry : entries) {
            total += entry.store().count(item);
        }
        return total;
    }

    public Map<NodeUuid, Long> breakdown(final Item item) {
        final Map<NodeUuid, Long> perServer = new LinkedHashMap<>();
        for (final Entry entry : entries) {
            final long count = entry.store().count(item);
            if (count > 0L) {
                perServer.merge(entry.node(), count, Long::sum);
            }
        }
        return perServer;
    }

    public long select(final Item item, final long amount, final IItemHandler destination) {
        return select(item, amount, destination, null);
    }

    public long select(final Item item, final long amount, final IItemHandler destination,
                       @Nullable final Set<NodeUuid> allowed) {
        long total = 0L;
        for (final long pulled : selectBreakdown(item, amount, destination, allowed).values()) {
            total += pulled;
        }
        return total;
    }

    public Map<NodeUuid, Long> selectBreakdown(final Item item, final long amount,
                                               final IItemHandler destination,
                                               @Nullable final Set<NodeUuid> allowed) {
        final Map<NodeUuid, Long> pulled = new LinkedHashMap<>();
        final int batchSize = Math.max(1, new ItemStack(item).getMaxStackSize());
        long moved = 0L;
        for (final Entry entry : entries) {
            if (allowed != null && !allowed.contains(entry.node())) {
                continue;
            }
            final ServerStore store = entry.store();
            long available = store.count(item);
            while (moved < amount && available > 0L) {
                final int batch = (int) Math.min(Math.min(amount - moved, available), batchSize);
                final ItemStack offered = new ItemStack(item, batch);
                final ItemStack leftover = ItemHandlerHelper.insertItem(destination, offered, false);
                final int accepted = batch - leftover.getCount();
                if (accepted <= 0) {
                    return pulled; // destination full
                }
                store.extract(item, accepted);
                moved += accepted;
                available -= accepted;
                pulled.merge(entry.node(), (long) accepted, Long::sum);
            }
            if (moved >= amount) {
                break;
            }
        }
        return pulled;
    }

    public int insert(final ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        final Item item = stack.getItem();
        long remaining = stack.getCount();
        for (final Entry entry : entries) {
            if (remaining <= 0L) {
                break;
            }
            remaining -= entry.store().insert(item, remaining);
        }
        return (int) (stack.getCount() - remaining);
    }

    public Map<NodeUuid, Long> insertBreakdown(final ItemStack stack) {
        final Map<NodeUuid, Long> stored = new LinkedHashMap<>();
        if (stack.isEmpty()) {
            return stored;
        }
        final Item item = stack.getItem();
        long remaining = stack.getCount();
        for (final Entry entry : entries) {
            if (remaining <= 0L) {
                break;
            }
            final long accepted = entry.store().insert(item, remaining);
            if (accepted > 0L) {
                stored.merge(entry.node(), accepted, Long::sum);
                remaining -= accepted;
            }
        }
        return stored;
    }

    public long drop(final Item item, final long amount) {
        long destroyed = 0L;
        for (final Entry entry : entries) {
            if (destroyed >= amount) {
                break;
            }
            destroyed += entry.store().extract(item, amount - destroyed);
        }
        return destroyed;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
