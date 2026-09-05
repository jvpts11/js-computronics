/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.tier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareEraTest {

    @Test
    void values_hasSixEras() {
        assertEquals(6, HardwareEra.values().length);
    }

    @Test
    void values_areOrderedVintageToSingularity() {
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

    @Test
    void fromLevel_returnsEraAtThatLevel() {
        assertEquals(HardwareEra.VINTAGE, HardwareEra.fromLevel(0));
        assertEquals(HardwareEra.STANDARD, HardwareEra.fromLevel(2));
        assertEquals(HardwareEra.SINGULARITY, HardwareEra.fromLevel(5));
    }

    @Test
    void fromLevel_isInverseOfLevel() {
        for (final HardwareEra era : HardwareEra.values()) {
            assertEquals(era, HardwareEra.fromLevel(era.level()));
        }
    }

    @Test
    void fromLevel_rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> HardwareEra.fromLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> HardwareEra.fromLevel(6));
    }

    @Test
    void bits_climbFromSixteenToSixtyFourAndStayThere() {
        assertEquals(16, HardwareEra.VINTAGE.bits());
        assertEquals(32, HardwareEra.LEGACY.bits());
        assertEquals(64, HardwareEra.STANDARD.bits());
        assertEquals(64, HardwareEra.SINGULARITY.bits());
    }

    @Test
    void mbPerItem_isTheLadderOneSixteenTwoFiftySix() {
        assertEquals(1L, HardwareEra.VINTAGE.mbPerItem());
        assertEquals(16L, HardwareEra.LEGACY.mbPerItem());
        assertEquals(256L, HardwareEra.STANDARD.mbPerItem());
        assertEquals(256L, HardwareEra.EXA.mbPerItem());
    }

    @Test
    void itemsFor_roundsAMegabyteSizeUpToWholeItems() {
        assertEquals(0L, HardwareEra.STANDARD.itemsFor(0));
        assertEquals(1L, HardwareEra.STANDARD.itemsFor(1));
        assertEquals(80L, HardwareEra.STANDARD.itemsFor(20_480));
        assertEquals(81L, HardwareEra.STANDARD.itemsFor(20_481));
        assertEquals(20_480L, HardwareEra.VINTAGE.itemsFor(20_480));
        assertEquals(3L, HardwareEra.LEGACY.itemsFor(48));
    }

    @Test
    void bytesPerMbEq_isAThousandthOfAnItemInBytes() {
        assertEquals(1_048L, HardwareEra.VINTAGE.bytesPerMbEq());
        assertEquals(16_777L, HardwareEra.LEGACY.bytesPerMbEq());
        assertEquals(268_435L, HardwareEra.STANDARD.bytesPerMbEq());
    }
}
