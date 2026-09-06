/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.hardware;

import dev.jstech.core.tier.HardwareEra;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpuSpecTest {

    @Test
    void orchestrationCapacity_minimalVintageCpu_isOne() {
        // 486SX-class: 1 core at 25 MHz -> 1 x 25 x 40 = 1000, rounds to 1 item/tick
        final CpuSpec cpu = new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 25, 3, false);
        assertEquals(1L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_roundsToNearest() {
        // 486DX2-class: 1 core at 66 MHz -> 2.64, rounds to 3 (not 2)
        final CpuSpec cpu = new CpuSpec(HardwareEra.VINTAGE, CpuSocket.SOCKET_3, 1, 66, 5, false);
        assertEquals(3L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_serverCpu() {
        // Epic 9654-class: 96 cores at 2400 MHz -> 9,216
        final CpuSpec cpu = new CpuSpec(HardwareEra.EXA, CpuSocket.SP5, 96, 2400, 360, false);
        assertEquals(9_216L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_quantumFlagship() {
        // Quantum flagship: 256 cores at 6000 MHz -> 61,440
        final CpuSpec cpu = new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_Q, 256, 6000, 500, false);
        assertEquals(61_440L, cpu.orchestrationCapacity());
    }

    @Test
    void orchestrationCapacity_alienAppliesTripleMultiplier() {
        // EM core: 128 cores at 5500 MHz -> 28,160, tripled by the alien factor -> 84,480
        final CpuSpec cpu = new CpuSpec(HardwareEra.SINGULARITY, CpuSocket.SOCKET_EM, 128, 5500, 450, true);
        assertEquals(84_480L, cpu.orchestrationCapacity());
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
