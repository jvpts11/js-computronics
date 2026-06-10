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
import dev.jsc.jscomputronics.module.computing.storage.DataSink;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    public static NetworkStorage ofServers(final ServerLevel level, final java.util.Collection<NodeUuid> nodes) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<Entry> entries = new ArrayList<>();
        for (final NodeUuid node : nodes) {
            system.locationOf(node).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    entries.add(new Entry(node, rack.getServerStorage(loc.slot())));
                }
            });
        }
        return new NetworkStorage(entries);
    }

    public Map<StorageKey, Long> query() {
        final Map<StorageKey, Long> totals = new HashMap<>();
        for (final Entry entry : entries) {
            entry.store().view().forEach((key, count) -> totals.merge(key, count, Long::sum));
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

    public long count(final StorageKey key) {
        long total = 0L;
        for (final Entry entry : entries) {
            total += entry.store().count(key);
        }
        return total;
    }

    public Map<NodeUuid, Long> breakdown(final StorageKey key) {
        final Map<NodeUuid, Long> perServer = new LinkedHashMap<>();
        for (final Entry entry : entries) {
            final long count = entry.store().count(key);
            if (count > 0L) {
                perServer.merge(entry.node(), count, Long::sum);
            }
        }
        return perServer;
    }

    public long select(final StorageKey key, final long amount, final DataSink destination) {
        return select(key, amount, destination, null);
    }

    public long select(final Item item, final long amount, final DataSink destination) {
        return select(StorageKey.of(item), amount, destination, null);
    }

    public long select(final StorageKey key, final long amount, final DataSink destination,
                       @Nullable final Set<NodeUuid> allowed) {
        long total = 0L;
        for (final long pulled : selectBreakdown(key, amount, destination, allowed).values()) {
            total += pulled;
        }
        return total;
    }

    public Map<NodeUuid, Long> selectBreakdown(final StorageKey key, final long amount,
                                               final DataSink destination,
                                               @Nullable final Set<NodeUuid> allowed) {
        final Map<NodeUuid, Long> pulled = new LinkedHashMap<>();
        final long batchSize = key.batch();
        long moved = 0L;
        for (final Entry entry : entries) {
            if (allowed != null && !allowed.contains(entry.node())) {
                continue;
            }
            final ServerStore store = entry.store();
            long available = store.count(key);
            while (moved < amount && available > 0L) {
                final long batch = Math.min(Math.min(amount - moved, available), batchSize);
                final long accepted = destination.insert(key, batch, false);
                if (accepted <= 0L) {
                    return pulled; // destination full
                }
                store.extract(key, accepted);
                moved += accepted;
                available -= accepted;
                pulled.merge(entry.node(), accepted, Long::sum);
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
        final StorageKey key = StorageKey.of(stack);
        long remaining = stack.getCount();
        for (final Entry entry : entries) {
            if (remaining <= 0L) {
                break;
            }
            remaining -= entry.store().insert(key, remaining);
        }
        return (int) (stack.getCount() - remaining);
    }

    public Map<NodeUuid, Long> insertBreakdown(final ItemStack stack) {
        final Map<NodeUuid, Long> stored = new LinkedHashMap<>();
        if (stack.isEmpty()) {
            return stored;
        }
        final StorageKey key = StorageKey.of(stack);
        long remaining = stack.getCount();
        for (final Entry entry : entries) {
            if (remaining <= 0L) {
                break;
            }
            final long accepted = entry.store().insert(key, remaining);
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
