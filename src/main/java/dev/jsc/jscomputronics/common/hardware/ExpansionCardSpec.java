/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

/**
 * What every PCIe expansion card exposes to a {@link ComputerBuild}, regardless of what the card does: the bus generation it needs (to check it fits the board), its power draw, and its {@link ExpansionCardKind}.
 *
 * <p>The card kinds are a closed set, so this is sealed to exactly the three concrete specs. That lets callers rely on {@link #kind()} being exhaustive and keeps a stray external implementation from claiming a slot.
 */
public sealed interface ExpansionCardSpec
        permits GpuSpec, CraftingCardSpec, PhiCoprocessorSpec {

    PcieGeneration bus();

    int tdpWatts();

    ExpansionCardKind kind();
}
