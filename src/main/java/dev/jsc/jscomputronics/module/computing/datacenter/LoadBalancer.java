/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.datacenter;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies a datacenter section's {@link LoadBalanceMode} to how its Servers are ordered and how an INSERT spreads across them.
 */
public final class LoadBalancer {

    private LoadBalancer() {
    }

    public static List<NodeUuid> order(final List<NodeUuid> servers, final LoadBalanceMode mode,
                                       @Nullable final ServerLevel level, @Nullable final NetworkUuid network) {
        if (mode != LoadBalanceMode.LEAST_LOADED || level == null || network == null || servers.size() < 2) {
            return servers;
        }
        final NetworkSystem system = NetworkSystem.get(level);
        final List<NodeUuid> sorted = new ArrayList<>(servers);
        sorted.sort(Comparator.comparingLong((final NodeUuid node) -> freeWeightOf(system, level, node)).reversed());
        return sorted;
    }

    private static long freeWeightOf(final NetworkSystem system, final ServerLevel level, final NodeUuid node) {
        return system.locationOf(node)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack
                        ? rack.getServerStorage(loc.slot()).freeWeight()
                        : 0L)
                .orElse(0L);
    }

    public static long insert(final List<ServerStore> stores, final StorageKey key, final long amount,
                              final LoadBalanceMode mode) {
        if (stores.isEmpty() || amount <= 0L) {
            return 0L;
        }
        return switch (mode) {
            case LEAST_LOADED -> {
                final List<ServerStore> sorted = new ArrayList<>(stores);
                sorted.sort(Comparator.comparingLong(ServerStore::freeWeight).reversed());
                yield sequential(sorted, key, amount);
            }
            case ROUND_ROBIN -> roundRobin(stores, key, amount);
            case MANUAL -> sequential(stores, key, amount);
        };
    }

    private static long sequential(final List<ServerStore> stores, final StorageKey key, final long amount) {
        long remaining = amount;
        for (final ServerStore store : stores) {
            if (remaining <= 0L) {
                break;
            }
            remaining -= store.insert(key, remaining);
        }
        return amount - remaining;
    }

    private static long roundRobin(final List<ServerStore> stores, final StorageKey key, final long amount) {
        long remaining = amount;
        final long batch = Math.max(1L, key.batch());
        boolean progress = true;
        while (remaining > 0L && progress) {
            progress = false;
            for (final ServerStore store : stores) {
                if (remaining <= 0L) {
                    break;
                }
                final long put = store.insert(key, Math.min(remaining, batch));
                if (put > 0L) {
                    remaining -= put;
                    progress = true;
                }
            }
        }
        return amount - remaining;
    }
}
