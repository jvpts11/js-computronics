/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jsc.jscomputronics.common.tier.HardwareEra;

import java.util.Objects;

/**
 * Immutable specification of a CPU.
 */
public record CpuSpec(HardwareEra era,
                      CpuSocket socket,
                      int cores,
                      int freqMhz,
                      int tdpWatts,
                      boolean alien) {

    private static final int CAPACITY_FACTOR = 160;

    public CpuSpec {
        Objects.requireNonNull(era, "era must not be null");
        Objects.requireNonNull(socket, "socket must not be null");
        if (cores <= 0) {
            throw new IllegalArgumentException("cores must be > 0; got " + cores);
        }
        if (freqMhz <= 0) {
            throw new IllegalArgumentException("freqMhz must be > 0; got " + freqMhz);
        }
        if (tdpWatts < 0) {
            throw new IllegalArgumentException("tdpWatts must be >= 0; got " + tdpWatts);
        }
    }

    public long orchestrationCapacity() {
        final long multiplier = alien ? 3L : 1L;
        final long scaled = (long) cores * freqMhz * CAPACITY_FACTOR * multiplier;
        return (scaled + 500L) / 1000L; // round to nearest items/tick
    }
}
