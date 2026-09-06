/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.hardware;

/**
 * RAM generations, in chronological order. Each generation carries a {@link #latencyTicks} value
 * representing the access latency modelled by the virtual-thread staging gate: older DRAM types
 * have a noticeable staging delay before data is available to the network, while DDR5 and later
 * generations are fast enough that the delay is zero at the simulation tick granularity.
 */
public enum RamGeneration {

    SIMM(5),
    EDO(4),
    SDRAM(3),
    DDR(2),
    DDR2(2),
    DDR3(1),
    DDR4(1),
    DDR5(0),
    DDR6(0),
    HBM(0);

    private final int latencyTicks;

    RamGeneration(final int latencyTicks) {
        this.latencyTicks = latencyTicks;
    }

    /** Ticks a virtual thread parks before the RAM staging gate opens. Zero means no delay. */
    public int latencyTicks() {
        return latencyTicks;
    }
}
