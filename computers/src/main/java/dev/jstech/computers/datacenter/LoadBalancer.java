/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.datacenter;

import dev.jstech.computers.blockentity.ServerRackBlockEntity;
import dev.jstech.computers.storage.ServerStore;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
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
        return insert(stores, key, amount, mode, 0);
    }

    /**
     * Writes {@code amount} of {@code key} across a section's servers the way its mode says to.
     *
     * <p>{@code start} rotates which server goes first, so a mode that spreads work does not begin from the
     * same server every time: without it a run of one-item writes all landed on the first server, and the
     * setting looked like it did nothing. The caller keeps the rotation (the section's router) so it survives
     * between writes.
     */
    public static long insert(final List<ServerStore> stores, final StorageKey key, final long amount,
                              final LoadBalanceMode mode, final int start) {
        if (stores.isEmpty() || amount <= 0L) {
            return 0L;
        }
        return switch (mode) {
            case LEAST_LOADED -> {
                /*
                 * Rotate first, then sort: the sort is stable, so servers with equally free drives take turns
                 * instead of the first one in the list always winning the tie.
                 */
                final List<ServerStore> sorted = rotate(stores, start);
                sorted.sort(Comparator.comparingLong(ServerStore::freeWeight).reversed());
                yield sequential(sorted, key, amount);
            }
            case ROUND_ROBIN -> roundRobin(rotate(stores, start), key, amount);
            case MANUAL -> sequential(stores, key, amount);
        };
    }

    /** The same servers, beginning at {@code start}, as a fresh list, so the caller's order is untouched. */
    private static List<ServerStore> rotate(final List<ServerStore> stores, final int start) {
        final int n = stores.size();
        final int from = n == 0 ? 0 : Math.floorMod(start, n);
        final List<ServerStore> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(stores.get((from + i) % n));
        }
        return out;
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
        /*
         * An even share each, not a stack each: a stack-sized batch meant one deposit of 64 went entirely to
         * the first server, so round-robin behaved exactly like manual for everything a player carries.
         */
        final long batch = Math.max(1L, (amount + stores.size() - 1) / stores.size());
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
