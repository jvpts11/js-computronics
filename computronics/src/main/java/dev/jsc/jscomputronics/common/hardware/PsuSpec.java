/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

/**
 * Immutable specification of a power supply unit.
 *
 * <p>An auto-scaling PSU is one whose output is not a fixed wattage but dimensions itself to whatever
 * the rest of the build draws, so it is treated as always able to satisfy the power draw. The
 * {@code wattage} of an auto-scaling unit is only a nominal/display value and is not used to gate the
 * build's power check.
 */
public record PsuSpec(int wattage, int efficiencyPercent, boolean autoScaling) {

    public PsuSpec {
        if (wattage <= 0) {
            throw new IllegalArgumentException("wattage must be > 0; got " + wattage);
        }
        if (efficiencyPercent < 1 || efficiencyPercent > 100) {
            throw new IllegalArgumentException(
                    "efficiencyPercent must be in 1..100; got " + efficiencyPercent);
        }
    }

    /**
     * Convenience constructor for a conventional, fixed-wattage PSU (not auto-scaling).
     */
    public PsuSpec(final int wattage, final int efficiencyPercent) {
        this(wattage, efficiencyPercent, false);
    }
}
