/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

/**
 * How much of one disk's storage is public (visible to the network) versus private (owner-only), as a per-mille value 0..1000. Per-mille is used instead of a float so the slider snaps to exact integer steps with no rounding drift.
 */
public record DiskPrivacy(int publicPermille) {

    /** A disk exposing none of its contents to the network — the default for a fresh computer disk. */
    public static final DiskPrivacy FULLY_PRIVATE = new DiskPrivacy(0);

    /** A disk exposing all of its contents to the network (how a Server's disk always behaves). */
    public static final DiskPrivacy FULLY_PUBLIC = new DiskPrivacy(1000);

    public DiskPrivacy {
        publicPermille = clampPermille(publicPermille);
    }

    /** Clamps any value into the valid per-mille range so out-of-range input is corrected, never rejected. */
    public static int clampPermille(final int permille) {
        return Math.max(0, Math.min(1000, permille));
    }

    /**
     * The public share of a total, floored. Flooring (rather than rounding) guarantees the public share never exceeds the total and that public + private always reconstructs the total exactly.
     */
    public long publicShareOf(final long total) {
        if (total <= 0L) {
            return 0L;
        }
        // 64-bit product so a large weight times up to 1000 never overflows.
        return Math.floorDiv(total * publicPermille, 1000L);
    }

    /** The private remainder: whatever the public share does not cover. */
    public long privateShareOf(final long total) {
        if (total <= 0L) {
            return 0L;
        }
        return total - publicShareOf(total);
    }
}
