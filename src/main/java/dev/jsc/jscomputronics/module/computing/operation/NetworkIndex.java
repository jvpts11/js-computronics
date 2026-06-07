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
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
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

    private final Map<Item, List<ItemLocation>> catalog = new LinkedHashMap<>();
    private final StorageLockTable<Item> locks = new StorageLockTable<>();

    public void rebuild(final ServerLevel level, final NetworkUuid network) {
        catalog.clear();
        if (network == null) {
            return;
        }
        final NetworkSystem system = NetworkSystem.get(level);
        for (final ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    indexServer(rack, loc.slot(), server.nodeUuid());
                }
            });
        }
    }

    private void indexServer(final ServerRackBlockEntity rack, final int slot, final NodeUuid server) {
        final StorageTier tier = tierOf(rack, slot);
        rack.getServerStorage(slot).view().forEach((item, quantity) -> {
            if (quantity > 0L) {
                catalog.computeIfAbsent(item, it -> new ArrayList<>())
                        .add(new ItemLocation(server, tier, quantity));
            }
        });
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

    // Query (reads the in-RAM catalog, net of locks — never touches disks)

    public long available(final Item item) {
        long total = 0L;
        for (final ItemLocation location : catalog.getOrDefault(item, List.of())) {
            total += Math.max(0L, location.quantity() - locks.lockedOn(item, location.server()));
        }
        return total;
    }

    public List<ItemLocation> locations(final Item item) {
        final List<ItemLocation> out = new ArrayList<>();
        for (final ItemLocation location : catalog.getOrDefault(item, List.of())) {
            final long free = location.quantity() - locks.lockedOn(item, location.server());
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
                    final long free = rack.getServerStorage(loc.slot()).free();
                    if (free > 0L) {
                        out.add(new ItemLocation(server.nodeUuid(), tierOf(rack, loc.slot()), free));
                    }
                }
            });
        }
        return out;
    }

    public Map<Item, Long> snapshot() {
        final Map<Item, Long> out = new LinkedHashMap<>();
        for (final Item item : catalog.keySet()) {
            final long free = available(item);
            if (free > 0L) {
                out.put(item, free);
            }
        }
        return out;
    }

    // Locking (reservations for in-flight Operations)

    public Allocation lock(final UUID operation, final Item item, final long demand) {
        final Allocation plan = StorageAllocator.allocate(locations(item), demand);
        locks.lock(operation, item, plan.perServer());
        return plan;
    }

    public void release(final UUID operation, final Item item, final NodeUuid server, final long amount) {
        locks.release(operation, item, server, amount);
    }

    public void unlock(final UUID operation) {
        locks.unlock(operation);
    }

    public boolean isLocked(final UUID operation) {
        return locks.holdsLocks(operation);
    }

    public void clear() {
        catalog.clear();
        locks.clear();
    }
}
