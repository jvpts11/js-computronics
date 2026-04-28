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

class IndustrialTierTest {

    @Test
    void hasTenTiersFromT0ToT9() {
        assertEquals(10, IndustrialTier.values().length);
    }

    @Test
    void next_advancesByOne() {
        assertEquals(IndustrialTier.T1, IndustrialTier.T0.next());
        assertEquals(IndustrialTier.T4, IndustrialTier.T3.next());
        assertEquals(IndustrialTier.T9, IndustrialTier.T8.next());
    }

    @Test
    void next_clampsAtT9() {
        assertEquals(IndustrialTier.T9, IndustrialTier.T9.next());
    }

    @Test
    void prev_decrementsByOne() {
        assertEquals(IndustrialTier.T8, IndustrialTier.T9.prev());
        assertEquals(IndustrialTier.T0, IndustrialTier.T1.prev());
    }

    @Test
    void prev_clampsAtT0() {
        assertEquals(IndustrialTier.T0, IndustrialTier.T0.prev());
    }

    @Test
    void level_matchesOrdinal() {
        assertEquals(0, IndustrialTier.T0.level());
        assertEquals(5, IndustrialTier.T5.level());
        assertEquals(9, IndustrialTier.T9.level());
    }

    @Test
    void isAtLeast_acceptsEqualOrHigher() {
        assertTrue(IndustrialTier.T5.isAtLeast(IndustrialTier.T0));
        assertTrue(IndustrialTier.T5.isAtLeast(IndustrialTier.T5));
        assertFalse(IndustrialTier.T2.isAtLeast(IndustrialTier.T5));
    }

    @Test
    void isAtMost_acceptsEqualOrLower() {
        assertTrue(IndustrialTier.T2.isAtMost(IndustrialTier.T5));
        assertTrue(IndustrialTier.T2.isAtMost(IndustrialTier.T2));
        assertFalse(IndustrialTier.T7.isAtMost(IndustrialTier.T3));
    }
}
