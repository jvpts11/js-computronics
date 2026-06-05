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
 */
public record PsuSpec(int wattage, int efficiencyPercent) {

    public PsuSpec {
        if (wattage <= 0) {
            throw new IllegalArgumentException("wattage must be > 0; got " + wattage);
        }
        if (efficiencyPercent < 1 || efficiencyPercent > 100) {
            throw new IllegalArgumentException(
                    "efficiencyPercent must be in 1..100; got " + efficiencyPercent);
        }
    }
}
