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
 * Immutable specification of a GPU.
 */
public record GpuSpec(HardwareEra era,
                      PcieGeneration bus,
                      int cores,
                      int vramMb,
                      int tdpWatts) {

    public GpuSpec {
        Objects.requireNonNull(era, "era must not be null");
        Objects.requireNonNull(bus, "bus must not be null");
        if (cores <= 0) {
            throw new IllegalArgumentException("cores must be > 0; got " + cores);
        }
        if (vramMb < 0) {
            throw new IllegalArgumentException("vramMb must be >= 0; got " + vramMb);
        }
        if (tdpWatts < 0) {
            throw new IllegalArgumentException("tdpWatts must be >= 0; got " + tdpWatts);
        }
    }

    public long threads() {
        return (long) cores * 4L;
    }
}
