/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.format;

public enum Unit {
    FE(" FE", true),

    FE_PER_TICK(" FE/t", true),

    MB(" MB", true),

    IT_PER_TICK(" it/t", true);

    private final String suffix;
    private final boolean scalable;

    Unit(final String suffix, final boolean scalable) {
        this.suffix = suffix;
        this.scalable = scalable;
    }

    public String suffix() {
        return suffix;
    }

    public boolean scalable() {
        return scalable;
    }
}
