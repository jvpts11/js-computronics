/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyTierTest {

    @Test
    void t1Copper_hasThroughput500() {
        assertEquals(500L, EnergyTier.T1_COPPER.maxThroughput());
    }

    @Test
    void t2Aluminum_hasThroughput2000() {
        assertEquals(2_000L, EnergyTier.T2_ALUMINUM.maxThroughput());
    }

    @Test
    void t3HighCapacity_hasThroughput8000() {
        assertEquals(8_000L, EnergyTier.T3_HIGH_CAPACITY.maxThroughput());
    }

    @Test
    void t4SuperconductorLT_hasThroughput32000() {
        assertEquals(32_000L, EnergyTier.T4_SUPERCONDUCTOR_LT.maxThroughput());
    }

    @Test
    void t5SuperconductorHT_hasThroughput128000() {
        assertEquals(128_000L, EnergyTier.T5_SUPERCONDUCTOR_HT.maxThroughput());
    }

    @Test
    void t6Quantum_hasThroughput512000() {
        assertEquals(512_000L, EnergyTier.T6_QUANTUM.maxThroughput());
    }

    @Test
    void t7Singularity_hasUnlimitedThroughput() {
        assertEquals(Long.MAX_VALUE, EnergyTier.T7_SINGULARITY.maxThroughput());
        assertTrue(EnergyTier.T7_SINGULARITY.isUnlimited());
    }

    @Test
    void isUnlimited_isFalseForAllExceptT7() {
        assertFalse(EnergyTier.T1_COPPER.isUnlimited());
        assertFalse(EnergyTier.T2_ALUMINUM.isUnlimited());
        assertFalse(EnergyTier.T3_HIGH_CAPACITY.isUnlimited());
        assertFalse(EnergyTier.T4_SUPERCONDUCTOR_LT.isUnlimited());
        assertFalse(EnergyTier.T5_SUPERCONDUCTOR_HT.isUnlimited());
        assertFalse(EnergyTier.T6_QUANTUM.isUnlimited());
    }

    @Test
    void throughput_growsMonotonicallyFromT1ToT7() {
        EnergyTier[] tiers = EnergyTier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i].maxThroughput() > tiers[i - 1].maxThroughput(),
                    "Tier " + tiers[i] + " should have larger throughput than "
                            + tiers[i - 1]);
        }
    }

    @Test
    void translationKeys_areCanonical() {
        assertEquals("insulated_copper_cable", EnergyTier.T1_COPPER.translationKey());
        assertEquals("singularity_cable", EnergyTier.T7_SINGULARITY.translationKey());
    }
}
