/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.index;

import dev.jstech.core.uuid.NodeUuid;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides where an item amount is served from across the network, fastest tier first.
 */
public final class StorageAllocator {

    private StorageAllocator() {
    }

    public static Allocation allocate(final List<ItemLocation> sources, final long demand) {
        if (demand <= 0L || sources.isEmpty()) {
            return new Allocation(Map.of(), 0L);
        }
        final List<ItemLocation> ordered = sources.stream()
                .filter(location -> location.quantity() > 0L)
                .sorted(Comparator.comparingInt(ItemLocation::latencyTicks)
                        .thenComparing(Comparator.comparingLong(ItemLocation::quantity).reversed()))
                .toList();

        final Map<NodeUuid, Long> plan = new LinkedHashMap<>();
        long remaining = demand;
        for (final ItemLocation location : ordered) {
            if (remaining <= 0L) {
                break;
            }
            final long take = Math.min(remaining, location.quantity());
            if (take > 0L) {
                plan.merge(location.server(), take, Long::sum);
                remaining -= take;
            }
        }
        return new Allocation(plan, demand - remaining);
    }
}
