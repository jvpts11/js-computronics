/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jsc.jscomputronics.common.tier.IndustrialTier;

import java.util.Objects;

/**
 * A Crafting Card: the PCIe expansion card that lets a Crafting Computer execute recipes.
 */
public record CraftingCardSpec(IndustrialTier tier, PcieGeneration bus, double cpuFactor, int tdpWatts)
        implements ExpansionCardSpec {

    public CraftingCardSpec {
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(bus, "bus must not be null");
        if (cpuFactor <= 0) {
            throw new IllegalArgumentException("cpuFactor must be > 0; got " + cpuFactor);
        }
        if (tdpWatts < 0) {
            throw new IllegalArgumentException("tdpWatts must be >= 0; got " + tdpWatts);
        }
    }

    @Override
    public ExpansionCardKind kind() {
        return ExpansionCardKind.CRAFTING;
    }
}
