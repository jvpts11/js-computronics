/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jstech.core.tier.HardwareEra;

import java.util.Objects;

/**
 * Immutable specification of a RAM module.
 */
public record RamSpec(HardwareEra era,
                      RamGeneration generation,
                      long bufferItems,
                      int tdpWatts) {

    public RamSpec {
        Objects.requireNonNull(era, "era must not be null");
        Objects.requireNonNull(generation, "generation must not be null");
        if (bufferItems < 0) {
            throw new IllegalArgumentException("bufferItems must be >= 0; got " + bufferItems);
        }
        if (tdpWatts < 0) {
            throw new IllegalArgumentException("tdpWatts must be >= 0; got " + tdpWatts);
        }
    }

    /** Ticks a virtual thread parks for this module's staging latency. Delegates to {@link RamGeneration#latencyTicks()}. */
    public int latencyTicks() {
        return generation.latencyTicks();
    }
}
