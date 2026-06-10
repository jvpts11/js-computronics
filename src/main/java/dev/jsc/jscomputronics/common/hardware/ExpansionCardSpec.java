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
 */
public interface ExpansionCardSpec {

    PcieGeneration bus();

    int tdpWatts();

    ExpansionCardKind kind();
}
