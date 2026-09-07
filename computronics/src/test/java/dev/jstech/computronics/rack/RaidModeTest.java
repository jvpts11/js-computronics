/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.rack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RaidModeTest {

    @Test
    void usableCapacity_stripeSumsEveryDrive() {
        assertEquals(3000L, RaidMode.RAID0.usableCapacity(List.of(1000L, 2000L)));
    }

    @Test
    void usableCapacity_mirrorTakesTheSmallestDrive() {
        assertEquals(1000L, RaidMode.RAID1.usableCapacity(List.of(1000L, 2000L, 4000L)));
    }

    @Test
    void usableCapacity_parityCostsOneDrive() {
        assertEquals(4000L, RaidMode.RAID5.usableCapacity(List.of(2000L, 2000L, 2000L)));
    }

    @Test
    void usableCapacity_parityChargesTheSmallestMemberOnAMixedArray() {
        // (n-1) x smallest, so the survivors can always hold the whole volume after one is pulled.
        assertEquals(2000L, RaidMode.RAID5.usableCapacity(List.of(1000L, 2000L, 4000L)));
    }

    @Test
    void usableCapacity_survivorsCanAlwaysHoldADegradedVolume() {
        final List<List<Long>> arrays = List.of(
                List.of(1000L, 2000L, 4000L),
                List.of(500L, 500L, 500L, 8000L),
                List.of(2000L, 2000L, 2000L));
        for (final List<Long> drives : arrays) {
            final long volume = RaidMode.RAID5.usableCapacity(drives);
            long largest = 0L;
            long sum = 0L;
            for (final long d : drives) {
                largest = Math.max(largest, d);
                sum += d;
            }
            // The worst loss is the biggest member; what remains must still fit the whole volume.
            assertTrue(sum - largest >= volume,
                    "losing the biggest member must still leave room for " + volume + " in " + drives);
        }
    }

    @Test
    void usableCapacity_aDegradedArrayKeepsThePromisedSize() {
        /*
         * A 3-member parity array of 2000s presents 4000; losing one member costs redundancy, not
         * capacity, so the two survivors still report 4000.
         */
        assertEquals(4000L, RaidMode.RAID5.usableCapacity(List.of(2000L, 2000L, 2000L), 3));
        assertEquals(4000L, RaidMode.RAID5.usableCapacity(List.of(2000L, 2000L), 3));
        assertEquals(1000L, RaidMode.RAID1.usableCapacity(List.of(1000L), 3));
    }

    @Test
    void usableCapacity_isZeroWithNoDrivesLeft() {
        assertEquals(0L, RaidMode.RAID5.usableCapacity(List.of(), 3));
    }

    @Test
    void usableCapacity_isZeroBelowTheModeMinimum() {
        assertEquals(0L, RaidMode.RAID5.usableCapacity(List.of(2000L, 2000L)));
        assertEquals(0L, RaidMode.RAID1.usableCapacity(List.of(2000L)));
        assertEquals(0L, RaidMode.NONE.usableCapacity(List.of(2000L, 2000L)));
    }

    @Test
    void formsArray_needsTheModeMinimum() {
        assertFalse(RaidMode.NONE.formsArray(8));
        assertFalse(RaidMode.RAID5.formsArray(2));
        assertTrue(RaidMode.RAID5.formsArray(3));
        assertTrue(RaidMode.RAID0.formsArray(2));
    }

    @Test
    void survives_stripeLosesEverythingWithOneDrive() {
        assertTrue(RaidMode.RAID0.survives(3, 3));
        assertFalse(RaidMode.RAID0.survives(3, 2));
    }

    @Test
    void survives_mirrorHoldsDownToASingleDrive() {
        assertTrue(RaidMode.RAID1.survives(4, 1));
        assertFalse(RaidMode.RAID1.survives(4, 0));
    }

    @Test
    void survives_parityToleratesExactlyOneLoss() {
        assertTrue(RaidMode.RAID5.survives(4, 3));
        assertFalse(RaidMode.RAID5.survives(4, 2));
    }

    @Test
    void rebuildable_onlyForRedundantModesMissingAMember() {
        assertTrue(RaidMode.RAID1.rebuildable(3, 2));
        assertTrue(RaidMode.RAID5.rebuildable(3, 2));
        assertFalse(RaidMode.RAID0.rebuildable(3, 2), "a broken stripe has nothing to rebuild from");
        assertFalse(RaidMode.RAID5.rebuildable(3, 3), "a complete array needs no rebuild");
        assertFalse(RaidMode.RAID5.rebuildable(3, 1), "a dead array cannot rebuild");
    }

    @Test
    void throughputPercent_onlyTheStripeIsFaster() {
        assertEquals(125, RaidMode.RAID0.throughputPercent());
        assertEquals(100, RaidMode.RAID1.throughputPercent());
        assertEquals(100, RaidMode.RAID5.throughputPercent());
        assertEquals(100, RaidMode.NONE.throughputPercent());
    }
}
