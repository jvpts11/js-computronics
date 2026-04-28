/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 *
 * J's Computronics is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * as published by the Free Software Foundation.
 *
 * J's Computronics is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 */
package dev.jsc.jscomputronics.common.tier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareEraTest {

    @Test
    void hasSixErasFromVintageToSingularity() {
        assertEquals(6, HardwareEra.values().length);
    }

    @Test
    void erasAreOrderedAsExpected() {
        assertEquals(HardwareEra.VINTAGE, HardwareEra.values()[0]);
        assertEquals(HardwareEra.LEGACY, HardwareEra.values()[1]);
        assertEquals(HardwareEra.STANDARD, HardwareEra.values()[2]);
        assertEquals(HardwareEra.ADVANCED, HardwareEra.values()[3]);
        assertEquals(HardwareEra.EXA, HardwareEra.values()[4]);
        assertEquals(HardwareEra.SINGULARITY, HardwareEra.values()[5]);
    }

    @Test
    void next_advancesByOne() {
        assertEquals(HardwareEra.LEGACY, HardwareEra.VINTAGE.next());
        assertEquals(HardwareEra.ADVANCED, HardwareEra.STANDARD.next());
    }

    @Test
    void next_clampsAtSingularity() {
        assertEquals(HardwareEra.SINGULARITY, HardwareEra.SINGULARITY.next());
    }

    @Test
    void prev_decrementsByOne() {
        assertEquals(HardwareEra.EXA, HardwareEra.SINGULARITY.prev());
        assertEquals(HardwareEra.VINTAGE, HardwareEra.LEGACY.prev());
    }

    @Test
    void prev_clampsAtVintage() {
        assertEquals(HardwareEra.VINTAGE, HardwareEra.VINTAGE.prev());
    }

    @Test
    void isAtLeast_acceptsEqualOrHigher() {
        assertTrue(HardwareEra.ADVANCED.isAtLeast(HardwareEra.VINTAGE));
        assertTrue(HardwareEra.ADVANCED.isAtLeast(HardwareEra.ADVANCED));
        assertFalse(HardwareEra.LEGACY.isAtLeast(HardwareEra.EXA));
    }
}
