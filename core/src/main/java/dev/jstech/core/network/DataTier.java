/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.network;

/**
 * Canonical tiers for data network cables.
 */
public enum DataTier {
    T1_ETHERNET(500L, 64, "ethernet_cable"),
    T2_HBW(5_000L, 256, "hbw_cable"),
    T3_FIBER(7_000L, 1_024, "fiber_optic_cable"),
    T4_VLDC(5_000L, 10_000, "vldc_cable"),
    T6_QUANTUM(50_000L, 64, "quantum_interconnect_cable"),
    HPC(20_000L, 32, "hpc_cable"),
    /*
     * Dedicated short-range cable that links a Crafting Switch to its Crafting Computer. It carries machine
     * I/O coordination rather than bulk data, so its throughput is nominal; range is short (a local cluster).
     */
    CRAFTING(1_000L, 16, "crafting_cable");

    private final long maxThroughput;
    private final int maxLength;
    private final String translationKey;

    DataTier(final long maxThroughput,
             final int maxLength,
             final String translationKey) {
        this.maxThroughput = maxThroughput;
        this.maxLength = maxLength;
        this.translationKey = translationKey;
    }

    public long maxThroughput() {
        return maxThroughput;
    }

    public int maxLength() {
        return maxLength;
    }

    public String translationKey() {
        return translationKey;
    }
}
