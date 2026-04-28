package dev.jsc.jscomputronics.common.tier;

/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 *
 * J's Computronics is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * as published by the Free Software Foundation.
 *
 * J's Computronics is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 */

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
