/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataTierTest {

    @Test
    void t1Ethernet_hasThroughput500AndLength64() {
        assertEquals(500L, DataTier.T1_ETHERNET.maxThroughput());
        assertEquals(64, DataTier.T1_ETHERNET.maxLength());
    }

    @Test
    void t2Hbw_hasThroughput5000AndLength256() {
        assertEquals(5_000L, DataTier.T2_HBW.maxThroughput());
        assertEquals(256, DataTier.T2_HBW.maxLength());
    }

    @Test
    void t3Fiber_hasThroughput7000AndLength1024() {
        assertEquals(7_000L, DataTier.T3_FIBER.maxThroughput());
        assertEquals(1_024, DataTier.T3_FIBER.maxLength());
    }

    @Test
    void t4Vldc_hasThroughput5000AndLength10000() {
        assertEquals(5_000L, DataTier.T4_VLDC.maxThroughput());
        assertEquals(10_000, DataTier.T4_VLDC.maxLength());
    }

    @Test
    void t6Quantum_hasThroughput50000AndLength64() {
        // Quantum trades range for raw throughput.
        assertEquals(50_000L, DataTier.T6_QUANTUM.maxThroughput());
        assertEquals(64, DataTier.T6_QUANTUM.maxLength());
    }

    @Test
    void noT5Tier_existsInDataCables() {
        // Six data tiers (the numbered ladder plus the HPC cluster fabric), with no T5.
        DataTier[] tiers = DataTier.values();
        assertEquals(6, tiers.length);
        for (DataTier tier : tiers) {
            assertEquals(false, tier.name().contains("T5"));
        }
    }

    @Test
    void hpc_hasThroughput20000AndLength32() {
        assertEquals(20_000L, DataTier.HPC.maxThroughput());
        assertEquals(32, DataTier.HPC.maxLength());
        assertEquals("hpc_cable", DataTier.HPC.translationKey());
    }

    @Test
    void translationKeys_areCanonical() {
        assertEquals("ethernet_cable", DataTier.T1_ETHERNET.translationKey());
        assertEquals("quantum_interconnect_cable", DataTier.T6_QUANTUM.translationKey());
    }
}
