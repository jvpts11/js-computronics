/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import dev.jsc.jscomputronics.common.tier.IndustrialTier;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;

import java.util.Objects;

/**
 * Snapshot of a Server Router operating on a network.
 */
public record ServerRouterElement(
        NetworkUuid networkUuid,
        long pos,
        IndustrialTier tier
) implements NetworkTopologyElement {

    public ServerRouterElement {
        Objects.requireNonNull(networkUuid, "networkUuid must not be null");
        Objects.requireNonNull(tier, "tier must not be null");
    }

    public int maxRacks() {
        return maxRacksFor(tier);
    }

    public static int maxRacksFor(final IndustrialTier tier) {
        return switch (tier) {
            case T2 -> 4;
            case T3 -> 8;
            case T4 -> 16;
            case T5 -> 32;
            default -> 0;
        };
    }
}
