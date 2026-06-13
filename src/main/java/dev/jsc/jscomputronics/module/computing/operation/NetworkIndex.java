/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.network.ServerNode;
import dev.jsc.jscomputronics.common.operation.index.Allocation;
import dev.jsc.jscomputronics.common.operation.index.ItemLocation;
import dev.jsc.jscomputronics.common.operation.index.StorageAllocator;
import dev.jsc.jscomputronics.common.operation.index.StorageLockTable;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The live catalog of what the network's public storage holds, kept in the Mainframe's RAM.
 */
public final class NetworkIndex {

    private final Map<StorageKey, List<ItemLocation>> catalog = new LinkedHashMap<>();
    private final StorageLockTable<StorageKey> locks = new StorageLockTable<>();
    private final Map<NodeUuid, Long> indexedModCounts = new LinkedHashMap<>();
    // Player-issued holds: one reservation per item type, kept alive until an explicit unlock so that
    // every other Operation contending for that type WAITs. Distinct from the per-Operation locks above
    // (those are keyed by the Operation's id and freed when it settles).
    private final Map<StorageKey, ManualLock> manualLocks = new LinkedHashMap<>();

    private record ManualLock(UUID id, long amount) {
    }

    public void rebuild(final ServerLevel level, final NetworkUuid network) {
        catalog.clear();
        indexedModCounts.clear();
        if (network == null) {
            return;
        }
        final NetworkSystem system = NetworkSystem.get(level);
        for (final ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    indexServer(rack, loc.slot(), server.nodeUuid());
                    indexedModCounts.put(server.nodeUuid(), rack.storageModCount(loc.slot()));
                }
            });
        }
        // A Personal Computer contributes only its published share. With the default-private permille
        // this adds nothing until the owner moves a slider, so a fresh PC stays invisible to SELECT.
        for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(network)) {
            if (level.getBlockEntity(BlockPos.of(pc.pos())) instanceof PersonalComputerBlockEntity pcBe) {
                indexPc(pcBe, pc.nodeUuid());
                indexedModCounts.put(pc.nodeUuid(), pcBe.storageModCount());
            }
        }
    }

    public void analyzeIncremental(final ServerLevel level, final NetworkUuid network) {
        if (network == null) {
            catalog.clear();
            indexedModCounts.clear();
            return;
        }
        final NetworkSystem system = NetworkSystem.get(level);
        // Resolve the live servers and pick out the ones needing a re-read.
        final Map<NodeUuid, NetworkSystem.ServerLocation> live = new LinkedHashMap<>();
        final List<NodeUuid> dirty = new ArrayList<>();
        for (final ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    live.put(server.nodeUuid(), loc);
                    final Long seen = indexedModCounts.get(server.nodeUuid());
                    if (seen == null || seen != rack.storageModCount(loc.slot())) {
                        dirty.add(server.nodeUuid());
                    }
                }
            });
        }
        // The same changes-only pass for Personal Computers, keyed on the PC's storage counter (bumped
        // both by a disk-content change and by a slider write, so re-publishing re-reads the view).
        final Map<NodeUuid, PersonalComputerBlockEntity> livePcs = new LinkedHashMap<>();
        for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(network)) {
            if (level.getBlockEntity(BlockPos.of(pc.pos())) instanceof PersonalComputerBlockEntity pcBe) {
                livePcs.put(pc.nodeUuid(), pcBe);
                final Long seen = indexedModCounts.get(pc.nodeUuid());
                if (seen == null || seen != pcBe.storageModCount()) {
                    dirty.add(pc.nodeUuid());
                }
            }
        }
        // Nodes no longer on the network (or unresolvable) leave the catalog entirely.
        final List<NodeUuid> gone = new ArrayList<>();
        for (final NodeUuid indexed : indexedModCounts.keySet()) {
            if (!live.containsKey(indexed) && !livePcs.containsKey(indexed)) {
                gone.add(indexed);
            }
        }
        if (dirty.isEmpty() && gone.isEmpty()) {
            return; // nothing changed — the whole pass cost only counter comparisons
        }
        final java.util.Set<NodeUuid> stale = new java.util.HashSet<>(dirty);
        stale.addAll(gone);
        dropServers(stale);
        for (final NodeUuid node : gone) {
            indexedModCounts.remove(node);
        }
        for (final NodeUuid node : dirty) {
            final NetworkSystem.ServerLocation loc = live.get(node);
            if (loc != null
                    && level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                indexServer(rack, loc.slot(), node);
                indexedModCounts.put(node, rack.storageModCount(loc.slot()));
            } else {
                final PersonalComputerBlockEntity pcBe = livePcs.get(node);
                if (pcBe != null) {
                    indexPc(pcBe, node);
                    indexedModCounts.put(node, pcBe.storageModCount());
                }
            }
        }
    }

    public int vacuum(final ServerLevel level, final NetworkUuid network) {
        final java.util.Set<NodeUuid> registered = new java.util.HashSet<>();
        if (network != null) {
            final NetworkSystem system = NetworkSystem.get(level);
            for (final ServerNode server : system.serversOf(network)) {
                registered.add(server.nodeUuid());
            }
            // PCs are indexed too; keeping their nodes registered stops vacuum treating them as ghosts.
            for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(network)) {
                registered.add(pc.nodeUuid());
            }
        }
        int freed = 0;
        final var entries = catalog.entrySet().iterator();
        while (entries.hasNext()) {
            final List<ItemLocation> rows = entries.next().getValue();
            final var rowIt = rows.iterator();
            while (rowIt.hasNext()) {
                final ItemLocation row = rowIt.next();
                if (row.quantity() <= 0L || !registered.contains(row.server())) {
                    rowIt.remove();
                    freed++;
                }
            }
            if (rows.isEmpty()) {
                entries.remove();
            }
        }
        indexedModCounts.keySet().retainAll(registered);
        return freed;
    }

    private void dropServers(final java.util.Set<NodeUuid> servers) {
        final var entries = catalog.entrySet().iterator();
        while (entries.hasNext()) {
            final List<ItemLocation> rows = entries.next().getValue();
            rows.removeIf(row -> servers.contains(row.server()));
            if (rows.isEmpty()) {
                entries.remove();
            }
        }
    }

    private void indexServer(final ServerRackBlockEntity rack, final int slot, final NodeUuid server) {
        final StorageTier tier = tierOf(rack, slot);
        rack.getServerStorage(slot).view().forEach((key, quantity) -> {
            if (quantity > 0L) {
                catalog.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new ItemLocation(server, tier, quantity));
            }
        });
    }

    private void indexPc(final PersonalComputerBlockEntity pc, final NodeUuid node) {
        // A PC contributes only its published share, indexed at the slowest tier so it always sorts
        // last among SELECT sources — Servers are served first, a PC's published storage only as a
        // fallback. The private remainder is absent from publicView(), so SELECT can never reach it.
        pc.localStore().publicView().forEach((key, quantity) -> {
            if (quantity > 0L) {
                catalog.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new ItemLocation(node, StorageTier.HDD, quantity));
            }
        });
    }

    public static long serverThroughputCap(final ServerLevel level, final NodeUuid server) {
        return NetworkSystem.get(level).locationOf(server)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack
                        ? hardwareCapOf(rack, loc.slot()) : Long.MAX_VALUE)
                .orElse(Long.MAX_VALUE);
    }

    private static long hardwareCapOf(final ServerRackBlockEntity rack, final int slot) {
        final ItemStack stack = rack.getServers().getStackInSlot(slot);
        if (stack.getItem() instanceof ServerItem) {
            final ComputerBuild build = ServerItem.build(stack);
            if (build != null) {
                return Math.min(build.totalCapacity(), build.ramBuffer());
            }
        }
        return Long.MAX_VALUE;
    }

    private static StorageTier tierOf(final ServerRackBlockEntity rack, final int slot) {
        final ItemStack stack = rack.getServers().getStackInSlot(slot);
        if (stack.getItem() instanceof ServerItem) {
            final ComputerBuild build = ServerItem.build(stack);
            if (build != null) {
                return build.fastestDiskTier();
            }
        }
        return StorageTier.HDD;
    }

    /** Returns the best (lowest) RAM staging latency in ticks for the server. Zero if unresolvable. */
    public static int serverRamLatencyTicks(final ServerLevel level, final NodeUuid server) {
        return NetworkSystem.get(level).locationOf(server)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack
                        ? ramLatencyOf(rack, loc.slot()) : 0)
                .orElse(0);
    }

    private static int ramLatencyOf(final ServerRackBlockEntity rack, final int slot) {
        final ItemStack stack = rack.getServers().getStackInSlot(slot);
        if (stack.getItem() instanceof ServerItem) {
            final ComputerBuild build = ServerItem.build(stack);
            if (build != null) {
                return build.bestRamLatencyTicks();
            }
        }
        return 0;
    }

    // Query (reads the in-RAM catalog, net of locks — never touches disks)

    public long available(final Item item) {
        return available(StorageKey.of(item));
    }

    public long available(final StorageKey key) {
        long total = 0L;
        for (final ItemLocation location : catalog.getOrDefault(key, List.of())) {
            total += Math.max(0L, location.quantity() - locks.lockedOn(key, location.server()));
        }
        return total;
    }

    public long grossAvailable(final StorageKey key, final java.util.Set<NodeUuid> allowed) {
        long total = 0L;
        for (final ItemLocation location : catalog.getOrDefault(key, List.of())) {
            if (allowed == null || allowed.contains(location.server())) {
                total += location.quantity();
            }
        }
        return total;
    }

    public List<ItemLocation> locations(final StorageKey key) {
        final List<ItemLocation> out = new ArrayList<>();
        for (final ItemLocation location : catalog.getOrDefault(key, List.of())) {
            final long free = location.quantity() - locks.lockedOn(key, location.server());
            if (free > 0L) {
                out.add(location.withQuantity(free));
            }
        }
        return out;
    }

    public List<ItemLocation> freeSpace(final ServerLevel level, final NetworkUuid network) {
        final List<ItemLocation> out = new ArrayList<>();
        if (network == null) {
            return out;
        }
        final NetworkSystem system = NetworkSystem.get(level);
        for (final ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    final long freeWeight = rack.getServerStorage(loc.slot()).freeWeight();
                    if (freeWeight > 0L) {
                        out.add(new ItemLocation(server.nodeUuid(), tierOf(rack, loc.slot()), freeWeight));
                    }
                }
            });
        }
        return out;
    }

    public Map<StorageKey, Long> snapshot() {
        final Map<StorageKey, Long> out = new LinkedHashMap<>();
        for (final StorageKey key : catalog.keySet()) {
            final long free = available(key);
            if (free > 0L) {
                out.put(key, free);
            }
        }
        return out;
    }

    // Locking (reservations for in-flight Operations)

    public Allocation lock(final UUID operation, final StorageKey key, final long demand) {
        return lock(operation, key, demand, null);
    }

    public Allocation lock(final UUID operation, final Item item, final long demand) {
        return lock(operation, StorageKey.of(item), demand, null);
    }

    public Allocation lock(final UUID operation, final StorageKey key, final long demand,
                           final java.util.Set<NodeUuid> allowed) {
        final List<ItemLocation> sources;
        if (allowed == null) {
            sources = locations(key);
        } else {
            sources = new ArrayList<>();
            for (final ItemLocation location : locations(key)) {
                if (allowed.contains(location.server())) {
                    sources.add(location);
                }
            }
        }
        final Allocation plan = StorageAllocator.allocate(sources, demand);
        locks.lock(operation, key, plan.perServer());
        return plan;
    }

    public void release(final UUID operation, final StorageKey key, final NodeUuid server, final long amount) {
        locks.release(operation, key, server, amount);
    }

    public void unlock(final UUID operation) {
        locks.unlock(operation);
    }

    public boolean isLocked(final UUID operation) {
        return locks.holdsLocks(operation);
    }

    // Manual locking (player-issued holds that make concurrent Operations WAIT)

    /**
     * Reserves up to {@code demand} of {@code key} across the network under a standing hold, so that
     * every Operation that later contends for it WAITs. A second lock on a type already held is a no-op.
     *
     * @param allowed the servers the hold may draw from, or {@code null} for the whole network
     * @return the amount actually held (0 if the type was already locked or nothing was free to hold)
     */
    public long manualLock(final StorageKey key, final long demand, final java.util.Set<NodeUuid> allowed) {
        if (demand <= 0L || manualLocks.containsKey(key)) {
            return 0L;
        }
        final UUID id = UUID.randomUUID();
        final Allocation plan = lock(id, key, demand, allowed);
        if (plan.allocated() <= 0L) {
            locks.unlock(id); // reserved nothing — leave no empty holder behind
            return 0L;
        }
        manualLocks.put(key, new ManualLock(id, plan.allocated()));
        return plan.allocated();
    }

    /**
     * Releases the standing hold on {@code key}.
     *
     * @return the amount that was held (0 if the type was not manually locked)
     */
    public long manualUnlock(final StorageKey key) {
        final ManualLock held = manualLocks.remove(key);
        if (held == null) {
            return 0L;
        }
        locks.unlock(held.id());
        return held.amount();
    }

    public int manualUnlockAll() {
        final int count = manualLocks.size();
        for (final ManualLock held : manualLocks.values()) {
            locks.unlock(held.id());
        }
        manualLocks.clear();
        return count;
    }

    public boolean isManuallyLocked(final StorageKey key) {
        return manualLocks.containsKey(key);
    }

    public Map<StorageKey, Long> manualLockView() {
        final Map<StorageKey, Long> out = new LinkedHashMap<>();
        manualLocks.forEach((key, held) -> out.put(key, held.amount()));
        return out;
    }

    public void clear() {
        catalog.clear();
        locks.clear();
        indexedModCounts.clear();
        manualLocks.clear();
    }

    // Readout (for the Mainframe terminal's Maintenance tab)

    public int catalogSize() {
        return catalog.size();
    }

    public int indexedServerCount() {
        return indexedModCounts.size();
    }

    public int activeLockCount() {
        return locks.lockingOperationCount();
    }

    public long usedWeight() {
        long weight = 0L;
        for (final Map.Entry<StorageKey, List<ItemLocation>> entry : catalog.entrySet()) {
            for (final ItemLocation location : entry.getValue()) {
                weight += entry.getKey().weight(location.quantity());
            }
        }
        return weight;
    }

    // DROP (destruction — irreversible; only the Mainframe Maintenance tab calls this)

    @org.jetbrains.annotations.Nullable
    private static ServerStore storeOf(final ServerLevel level, final NodeUuid server) {
        return NetworkSystem.get(level).locationOf(server)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack
                        ? rack.getServerStorage(loc.slot()) : null)
                .orElse(null);
    }

    public long dropType(final ServerLevel level, final NetworkUuid network, final StorageKey key,
                         @org.jetbrains.annotations.Nullable final java.util.Set<NodeUuid> allowed) {
        if (network == null) {
            return 0L;
        }
        long destroyed = 0L;
        for (final ServerNode server : NetworkSystem.get(level).serversOf(network)) {
            if (allowed != null && !allowed.contains(server.nodeUuid())) {
                continue;
            }
            final ServerStore store = storeOf(level, server.nodeUuid());
            if (store != null) {
                destroyed += store.extract(key, store.count(key));
            }
        }
        return destroyed;
    }

    public long dropServer(final ServerLevel level, final NodeUuid server) {
        final ServerStore store = storeOf(level, server);
        if (store == null) {
            return 0L;
        }
        long destroyed = 0L;
        // Copy the keys first: extract mutates the store's view as it goes.
        for (final StorageKey key : new java.util.ArrayList<>(store.view().keySet())) {
            destroyed += store.extract(key, store.count(key));
        }
        return destroyed;
    }

    public long dropAll(final ServerLevel level, final NetworkUuid network) {
        if (network == null) {
            return 0L;
        }
        long destroyed = 0L;
        for (final ServerNode server : NetworkSystem.get(level).serversOf(network)) {
            destroyed += dropServer(level, server.nodeUuid());
        }
        return destroyed;
    }
}
