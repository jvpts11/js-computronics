/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.tier;

/**
 * Industrial Tier (T0–T9) — the central cross-module progression axis of J's Computronics.
 */

public enum IndustrialTier {
    T0, T1, T2, T3, T4, T5, T6, T7, T8, T9;

    public IndustrialTier next() {
        return this == T9 ? T9 : values()[ordinal() + 1];
    }

    public IndustrialTier prev() {
        return this == T0 ? T0 : values()[ordinal() - 1];
    }

    public int level() {
        return ordinal();
    }

    public boolean isAtLeast(IndustrialTier other) {
        return this.level() >= other.level();
    }

    public boolean isAtMost(IndustrialTier other) {
        return this.level() <= other.level();
    }
}
