/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RamGenerationTest {

    @Test
    void latencyTicks_decreasesOrHoldsAcrossGenerations() {
        final RamGeneration[] gens = RamGeneration.values();
        for (int i = 0; i < gens.length - 1; i++) {
            assertTrue(gens[i].latencyTicks() >= gens[i + 1].latencyTicks(),
                    gens[i] + " latency should be >= " + gens[i + 1] + " latency");
        }
    }

    @Test
    void latencyTicks_vintageGenerationsHaveDelay() {
        assertEquals(5, RamGeneration.SIMM.latencyTicks());
        assertEquals(4, RamGeneration.EDO.latencyTicks());
        assertEquals(3, RamGeneration.SDRAM.latencyTicks());
    }

    @Test
    void latencyTicks_ddrGenerationsHaveReducedDelay() {
        assertEquals(2, RamGeneration.DDR.latencyTicks());
        assertEquals(2, RamGeneration.DDR2.latencyTicks());
        assertEquals(1, RamGeneration.DDR3.latencyTicks());
        assertEquals(1, RamGeneration.DDR4.latencyTicks());
    }

    @Test
    void latencyTicks_modernGenerationsHaveNoDelay() {
        assertEquals(0, RamGeneration.DDR5.latencyTicks());
        assertEquals(0, RamGeneration.DDR6.latencyTicks());
        assertEquals(0, RamGeneration.HBM.latencyTicks());
    }

    @Test
    void latencyTicks_neverNegative() {
        for (final RamGeneration gen : RamGeneration.values()) {
            assertTrue(gen.latencyTicks() >= 0,
                    gen + " latency must not be negative");
        }
    }
}
