/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.index;

import dev.jstech.computers.hardware.StorageTier;
import dev.jstech.core.uuid.NodeUuid;

import java.util.Objects;

/**
 * One entry in a NetworkIndex query result: how much of an item a single server holds, on which
 * storage tier it lives, and the read latency the query actually pays for it. Latency is carried
 * separately from the tier because bay hardware can beat the raw disk, since a Cache Card in the bay's
 * gadget slot serves reads faster than the drives behind it.
 */
public record ItemLocation(NodeUuid server, StorageTier tier, long quantity, int latencyTicks) {

    public ItemLocation {
        Objects.requireNonNull(server, "server must not be null");
        Objects.requireNonNull(tier, "tier must not be null");
        if (quantity < 0L) {
            throw new IllegalArgumentException("quantity must be >= 0; got " + quantity);
        }
        if (latencyTicks < 0) {
            throw new IllegalArgumentException("latencyTicks must be >= 0; got " + latencyTicks);
        }
    }

    /** A location served at its tier's own latency (no cache hardware in front of it). */
    public ItemLocation(final NodeUuid server, final StorageTier tier, final long quantity) {
        this(server, tier, quantity, tier.latencyTicks());
    }

    public ItemLocation withQuantity(final long newQuantity) {
        return new ItemLocation(server, tier, newQuantity, latencyTicks);
    }
}
