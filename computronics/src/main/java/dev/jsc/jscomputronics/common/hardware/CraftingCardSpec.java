/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jstech.core.tier.IndustrialTier;

import java.util.Objects;

/**
 * A Crafting Card: a crafting-accelerator FPGA on a PCIe card. It "compiles" recipes into hardware pipelines and
 * runs them, offloading a Crafting Computer's crafting work from the CPU. Two stats matter: {@code threads} — how
 * many recipe/stage pipelines it can run in parallel (the ceiling on a computer's concurrent crafting stages) —
 * and {@code cpuFactor} — the crafting throughput it delivers, as a multiple of the host CPU's capacity.
 */
public record CraftingCardSpec(IndustrialTier tier, PcieGeneration bus, double cpuFactor, int threads, int tdpWatts)
        implements ExpansionCardSpec {

    public CraftingCardSpec {
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(bus, "bus must not be null");
        if (cpuFactor <= 0) {
            throw new IllegalArgumentException("cpuFactor must be > 0; got " + cpuFactor);
        }
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1; got " + threads);
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
