/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.tier;

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

    /**
     * The colour an era's screens are remembered by, as an RGB int for a tooltip: the green phosphor of a
     * CRT terminal for Vintage, the blue of the Legacy desktop's chrome, the accent blue of the Standard
     * desktop, and a colder cast for each generation past that. A part's era reads at a glance, before
     * the word does, which is what a player sorting a chest of boards and chips needs.
     */
    public int screenColor() {
        return switch (this) {
            case VINTAGE -> 0x33FF33;
            case LEGACY -> 0x245EDC;
            case STANDARD -> 0x0078D4;
            case ADVANCED -> 0x9B59FF;
            case EXA -> 0x00E5FF;
            case SINGULARITY -> 0xF2F2F2;
        };
    }

    /** The word size of the era's processors; what an item costs on the era's disks follows from it. */
    public int bits() {
        return switch (this) {
            case VINTAGE -> 16;
            case LEGACY -> 32;
            default -> 64;
        };
    }

    /**
     * The megabytes one item — or a bucket of fluid, which weighs the same — takes on a disk of this era: a
     * wider word makes a bigger record. Older hardware therefore packs more into the same megabytes, which
     * is how a 20 MB vintage drive holds anything at all, and a 500 GB standard one holds 2 000 items.
     */
    public long mbPerItem() {
        return switch (this) {
            case VINTAGE -> 1L;
            case LEGACY -> 16L;
            default -> 256L;
        };
    }

    /** How many items a size in megabytes costs on this era's disks, rounded up: a system image, say. */
    public long itemsFor(final long mb) {
        if (mb <= 0L) {
            return 0L;
        }
        final long per = mbPerItem();
        return (mb + per - 1L) / per;
    }

    /**
     * The bytes one thousandth of an item weighs on this era's disks — an item weighs 1 000 of those
     * units, the same unit a millibucket of fluid weighs one of. A file of N bytes costs
     * {@code ceil(N / bytesPerMbEq())} of them.
     */
    public long bytesPerMbEq() {
        return mbPerItem() * 1024L * 1024L / 1000L;
    }

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
