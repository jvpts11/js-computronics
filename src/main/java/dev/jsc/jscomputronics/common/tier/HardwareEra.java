/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.tier;

/**
 * Hardware Era — the progression axis for computational hardware (motherboards, CPUs, RAM, storage).
 */
public enum HardwareEra {
    VINTAGE,
    LEGACY,
    STANDARD,
    ADVANCED,
    EXA,
    SINGULARITY;

    public HardwareEra next() {
        return this == SINGULARITY ? SINGULARITY : values()[ordinal() + 1];
    }

    public HardwareEra prev() {
        return this == VINTAGE ? VINTAGE : values()[ordinal() - 1];
    }

    public int level() {
        return ordinal();
    }

    public boolean isAtLeast(HardwareEra other) {
        return this.level() >= other.level();
    }

    public boolean isAtMost(HardwareEra other) {
        return this.level() <= other.level();
    }
}
