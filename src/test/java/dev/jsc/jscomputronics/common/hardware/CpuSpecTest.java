/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpuSpecTest {

    @Test
    void orchestrationCapacity_minimalVintageCpu_isFour() {
        // 486SX-class: 1 core at 25 MHz -> 1 x 0.025 x 160 = 4
        final CpuSpec cpu = new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 25, 3, false);
        assertEquals(4L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_roundsToNearest() {
        // 486DX2-class: 1 core at 66 MHz -> 10.56, rounds to 11 (not 10)
        final CpuSpec cpu = new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 66, 5, false);
        assertEquals(11L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_serverCpu() {
        // Epic 9654-class: 96 cores at 2400 MHz -> 36,864
        final CpuSpec cpu = new CpuSpec(HardwareEra.EXA, CpuSocket.SP5, 96, 2400, 360, false);
        assertEquals(36_864L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_quantumFlagship() {
        // Quantum flagship: 256 cores at 6000 MHz -> 245,760
        final CpuSpec cpu = new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_Q, 256, 6000, 500, false);
        assertEquals(245_760L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_alienAppliesTripleMultiplier() {
        // EM core: 128 cores at 5500 MHz -> 112,640, tripled by the alien factor -> 337,920
        final CpuSpec cpu = new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_EM, 128, 5500, 450, true);
        assertEquals(337_920L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_nonAlienEquivalentIsOneThird() {
        final CpuSpec alien = new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_EM, 128, 5500, 450, true);
        final CpuSpec plain = new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_EM, 128, 5500, 450, false);
        assertEquals(plain.orchestrationCapacity() * 3, alien.orchestrationCapacity());
    }

    @Test
    void constructor_rejectsNonPositiveCores() {
        assertThrows(IllegalArgumentException.class,
                () -> new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 0, 25, 3, false));
    }

    @Test
    void constructor_rejectsNonPositiveFrequency() {
        assertThrows(IllegalArgumentException.class,
                () -> new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 0, 3, false));
    }

    @Test
    void constructor_rejectsNullSocket() {
        assertThrows(NullPointerException.class,
                () -> new CpuSpec(HardwareEra.VINTAGE, null, 1, 25, 3, false));
    }
}
