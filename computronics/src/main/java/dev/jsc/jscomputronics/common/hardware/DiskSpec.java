/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jsc.jscomputronics.common.tier.HardwareEra;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable specification of a storage disk. Capacity is counted in items; what that is in megabytes
 * follows from the era the disk was made for ({@link HardwareEra#mbPerItem()}), so a 20 MB vintage drive
 * and a 500 GB standard one are both honest about their nameplate.
 */
public record DiskSpec(StorageTier tier, HardwareEra era, long capacityItems, int tdpWatts) {

    public DiskSpec {
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(era, "era must not be null");
        if (capacityItems < 0) {
            throw new IllegalArgumentException("capacityItems must be >= 0; got " + capacityItems);
        }
        if (tdpWatts < 0) {
            throw new IllegalArgumentException("tdpWatts must be >= 0; got " + tdpWatts);
        }
    }

    /** The nameplate size: the items it holds at its era's cost per item. */
    public long capacityMb() {
        return capacityItems * era.mbPerItem();
    }

    /** A size in megabytes the way a drive label writes it: {@code 20 MB}, {@code 1.5 GB}, {@code 8 TB}. */
    public static String sizeLabel(final long mb) {
        if (mb < 1024L) {
            return mb + " MB";
        }
        final double gb = mb / 1024.0;
        if (gb < 1024.0) {
            return trim(gb) + " GB";
        }
        return trim(gb / 1024.0) + " TB";
    }

    private static String trim(final double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
