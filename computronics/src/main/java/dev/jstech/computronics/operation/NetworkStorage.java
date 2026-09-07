/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.storage.IDataSink;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.network.ServerNode;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
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
     * One node's store paired with the node identity that selects it for filtering, and whether it accepts inserts (a PC's public area is a read-only SELECT-source).
     */
    private record Entry(NodeUuid node, INodeStore store, boolean acceptsInsert) {
    }

    private final List<Entry> entries;
    private final Map<NodeUuid, Entry> byNode;

    private NetworkStorage(final List<Entry> entries) {
        this.entries = entries;
        this.byNode = new HashMap<>(entries.size() * 2);
        for (final Entry entry : entries) {
            byNode.putIfAbsent(entry.node(), entry);
        }
    }

    /**
     * The view built for one network this tick. Every Operation ticking on a network asks for the same
     * view, and building it walks every server of the network, so it is built once per tick and shared:
     * the stores inside are live, only the roster is a snapshot, and a server that leaves mid-tick reads
     * as empty through {@link ServerNodeStore} rather than through a stale rack.
     */
    private record TickView(ServerLevel level, long gameTime, NetworkStorage storage) {
    }

    private static final Map<NetworkUuid, TickView> VIEWS = new HashMap<>();

    public static NetworkStorage of(final ServerLevel level, final NetworkUuid network) {
        final long now = level.getGameTime();
        final TickView cached = VIEWS.get(network);
        if (cached != null && cached.level() == level && cached.gameTime() == now) {
            return cached.storage();
        }
        // A view is good for one tick. Anything older only pins the racks (and their drives) of a network
        // nobody is asking about any more, possibly in a level that has since unloaded.
        VIEWS.values().removeIf(view -> view.gameTime() != now || view.level() != level);
        final NetworkStorage built = build(level, network);
        VIEWS.put(network, new TickView(level, now, built));
        return built;
    }

    /** Whether any cached view predates {@code now}; for tests of the eviction above. */
    public static boolean holdsStaleViews(final long now) {
        for (final TickView view : VIEWS.values()) {
            if (view.gameTime() != now) {
                return true;
            }
        }
        return false;
    }

    private static NetworkStorage build(final ServerLevel level, final NetworkUuid network) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<Entry> entries = new ArrayList<>();
        for (final ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    entries.add(new Entry(server.nodeUuid(),
                            new ServerNodeStore(rack.getServerStorage(loc.slot())), true));
                }
            });
        }
        // A Personal Computer contributes only the published share of its disks, as a SELECT-source.
        // With the default-private permille this list is empty until the owner publishes some storage.
        for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(network)) {
            if (level.getBlockEntity(BlockPos.of(pc.pos())) instanceof PersonalComputerBlockEntity pcBe) {
                entries.add(new Entry(pc.nodeUuid(), new PcPublicNodeStore(pcBe.localStore()), false));
            }
        }
        return new NetworkStorage(entries);
    }

    public static NetworkStorage ofServers(final ServerLevel level, final java.util.Collection<NodeUuid> nodes) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<Entry> entries = new ArrayList<>();
        for (final NodeUuid node : nodes) {
            system.locationOf(node).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    entries.add(new Entry(node, new ServerNodeStore(rack.getServerStorage(loc.slot())), true));
                }
            });
        }
        return new NetworkStorage(entries);
    }

    /**
     * How many items the whole network can hold: every server's drives, plus whatever share the personal
     * computers on it have published.
     */
    public long capacity() {
        long sum = 0L;
        for (final Entry entry : entries) {
            sum += entry.store().capacity();
        }
        return sum;
    }

    /** How many it is holding, counted the same way, so the two are always comparable. */
    public long used() {
        long sum = 0L;
        for (final Entry entry : entries) {
            sum += entry.store().used();
        }
        return sum;
    }

    /** The same pair for one node, or zeroes when it is not on this network. */
    public long capacityOf(final NodeUuid node) {
        final Entry entry = byNode.get(node);
        return entry == null ? 0L : entry.store().capacity();
    }

    /** How many items that node is holding. */
    public long usedOf(final NodeUuid node) {
        final Entry entry = byNode.get(node);
        return entry == null ? 0L : entry.store().used();
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

    public long select(final StorageKey key, final long amount, final IDataSink destination) {
        return select(key, amount, destination, null);
    }

    public long select(final Item item, final long amount, final IDataSink destination) {
        return select(StorageKey.of(item), amount, destination, null);
    }

    public long select(final StorageKey key, final long amount, final IDataSink destination,
                       @Nullable final Set<NodeUuid> allowed) {
        long total = 0L;
        for (final long pulled : selectBreakdown(key, amount, destination, allowed).values()) {
            total += pulled;
        }
        return total;
    }

    public Map<NodeUuid, Long> selectBreakdown(final StorageKey key, final long amount,
                                               final IDataSink destination,
                                               @Nullable final Set<NodeUuid> allowed) {
        final Map<NodeUuid, Long> pulled = new LinkedHashMap<>();
        long moved = 0L;
        for (final Entry entry : entries) {
            if (allowed != null && !allowed.contains(entry.node())) {
                continue;
            }
            final long wanted = Math.min(amount - moved, entry.store().count(key));
            final long got = pull(entry.store(), key, amount - moved, destination);
            if (got > 0L) {
                moved += got;
                pulled.merge(entry.node(), got, Long::sum);
            }
            if (moved >= amount || got < wanted) {
                break; // done, or the destination is full and no other node can help
            }
        }
        return pulled;
    }

    /**
     * Pulls up to {@code amount} of {@code key} from one node straight into the destination — the per-source
     * step of a SELECT, found by node in constant time instead of a walk over the whole network.
     */
    public long pullFrom(final NodeUuid node, final StorageKey key, final long amount, final IDataSink destination) {
        final Entry entry = byNode.get(node);
        return entry == null ? 0L : pull(entry.store(), key, amount, destination);
    }

    /** Moves batches of {@code key} from one store into the destination until the amount, the store or the destination runs out. */
    private static long pull(final INodeStore store, final StorageKey key, final long amount, final IDataSink destination) {
        final long batchSize = key.batch();
        long moved = 0L;
        long available = store.count(key);
        while (moved < amount && available > 0L) {
            final long batch = Math.min(Math.min(amount - moved, available), batchSize);
            final long accepted = destination.insert(key, batch, false);
            if (accepted <= 0L) {
                break; // destination full
            }
            store.extract(key, accepted);
            moved += accepted;
            available -= accepted;
        }
        return moved;
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
            if (!entry.acceptsInsert()) {
                continue; // never write into a PC's public area
            }
            remaining -= entry.store().insert(key, remaining);
        }
        return (int) (stack.getCount() - remaining);
    }

    /**
     * Inserts {@code amount} of a key (item OR fluid) into the network, returning how much was stored. This is
     * the key-typed counterpart of {@link #insert(ItemStack)}, used to put a machine craft's fluid (or item)
     * output back into the network.
     */
    public long insert(final StorageKey key, final long amount) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        long remaining = amount;
        for (final Entry entry : entries) {
            if (remaining <= 0L) {
                break;
            }
            if (!entry.acceptsInsert()) {
                continue; // never write into a PC's public area
            }
            remaining -= entry.store().insert(key, remaining);
        }
        return amount - remaining;
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
            if (!entry.acceptsInsert()) {
                continue; // never write into a PC's public area
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
        final StorageKey key = StorageKey.of(item);
        long destroyed = 0L;
        for (final Entry entry : entries) {
            if (destroyed >= amount) {
                break;
            }
            destroyed += entry.store().extract(key, amount - destroyed);
        }
        return destroyed;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
