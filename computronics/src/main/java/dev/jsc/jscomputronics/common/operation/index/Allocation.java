/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation.index;

import dev.jstech.core.uuid.NodeUuid;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A plan for serving (or locking) an amount of one item across servers: how much comes from each server, and the total that could be covered.
 */
public record Allocation(Map<NodeUuid, Long> perServer, long allocated) {

    public Allocation {
        // Keep the caller's fastest-tier-first iteration order (Map.copyOf would discard it).
        perServer = Collections.unmodifiableMap(new LinkedHashMap<>(perServer));
        if (allocated < 0L) {
            throw new IllegalArgumentException("allocated must be >= 0; got " + allocated);
        }
    }

    public boolean covers(final long demand) {
        return allocated >= demand;
    }

    public boolean isEmpty() {
        return perServer.isEmpty();
    }
}
