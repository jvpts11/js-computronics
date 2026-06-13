/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import dev.jsc.jscomputronics.common.tier.HardwareEra;

/**
 * A computer block whose hardware era is fixed by its chassis rather than by the board installed in it. Per-era
 * computer blocks (the Vintage and Legacy variants) report their era here so their GUI wears the right era skin
 * even before any board is installed. Computers with no era-specific chassis (which take their look from the
 * installed board) simply do not implement this; their display era falls back to the board's era.
 */
public interface EraChassisBlock {

    /** The hardware era this chassis belongs to; never {@code null}. */
    HardwareEra chassisEra();
}
