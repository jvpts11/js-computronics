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
 *
 * <p>This is pure domain logic with no Minecraft dependency, so it stays unit-testable. When an era
 * has to back a block-state property, the binding layer stores its {@link #level()} as an
 * {@code IntegerProperty} and turns the stored value back into an era with {@link #fromLevel(int)};
 * that keeps the Minecraft-aware property type out of this enum.
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

    /**
     * The era whose {@link #level()} equals {@code level}. This is the inverse of {@link #level()}, used
     * by the binding layer to turn a block-state {@code IntegerProperty} value back into an era.
     *
     * @throws IllegalArgumentException if no era has that level
     */
    public static HardwareEra fromLevel(final int level) {
        final HardwareEra[] all = values();
        if (level < 0 || level >= all.length) {
            throw new IllegalArgumentException("no hardware era at level " + level);
        }
        return all[level];
    }

    public boolean isAtLeast(HardwareEra other) {
        return this.level() >= other.level();
    }

    public boolean isAtMost(HardwareEra other) {
        return this.level() <= other.level();
    }
}
