/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation.index;

import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;

import java.util.Objects;

/**
 * One entry in a NetworkIndex query result: how much of an item a single server holds, and on which storage tier it lives.
 */
public record ItemLocation(NodeUuid server, StorageTier tier, long quantity) {

    public ItemLocation {
        Objects.requireNonNull(server, "server must not be null");
        Objects.requireNonNull(tier, "tier must not be null");
        if (quantity < 0L) {
            throw new IllegalArgumentException("quantity must be >= 0; got " + quantity);
        }
    }

    public ItemLocation withQuantity(final long newQuantity) {
        return new ItemLocation(server, tier, newQuantity);
    }
}
