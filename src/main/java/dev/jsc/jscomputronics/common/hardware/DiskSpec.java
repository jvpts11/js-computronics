/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import java.util.Objects;

/**
 * Immutable specification of a storage disk.
 */
public record DiskSpec(StorageTier tier, long capacityItems, int tdpWatts) {

    public static final long MB_PER_ITEM = 256L;

    public DiskSpec {
        Objects.requireNonNull(tier, "tier must not be null");
        if (capacityItems < 0) {
            throw new IllegalArgumentException("capacityItems must be >= 0; got " + capacityItems);
        }
        if (tdpWatts < 0) {
            throw new IllegalArgumentException("tdpWatts must be >= 0; got " + tdpWatts);
        }
    }

    public long capacityMb() {
        return capacityItems * MB_PER_ITEM;
    }
}
