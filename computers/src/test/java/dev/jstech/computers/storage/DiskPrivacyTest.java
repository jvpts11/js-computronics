/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiskPrivacyTest {

    @Test
    void constructor_clampsBelowZeroToZero() {
        assertEquals(0, new DiskPrivacy(-200).publicPermille());
    }

    @Test
    void constructor_clampsAboveThousandToThousand() {
        assertEquals(1000, new DiskPrivacy(5000).publicPermille());
    }

    @Test
    void clampPermille_leavesInRangeValueUnchanged() {
        assertEquals(450, DiskPrivacy.clampPermille(450));
    }

    @Test
    void publicShareOf_zeroPermilleIsAllPrivate() {
        final DiskPrivacy privacy = DiskPrivacy.FULLY_PRIVATE;
        assertEquals(0L, privacy.publicShareOf(1000L));
        assertEquals(1000L, privacy.privateShareOf(1000L));
    }

    @Test
    void publicShareOf_thousandPermilleIsAllPublic() {
        final DiskPrivacy privacy = DiskPrivacy.FULLY_PUBLIC;
        assertEquals(1000L, privacy.publicShareOf(1000L));
        assertEquals(0L, privacy.privateShareOf(1000L));
    }

    @Test
    void publicShareOf_halfRoundsDownByFloor() {
        // 7 * 500 / 1000 = 3.5 -> floored to 3, the remaining 4 stay private (sum equals the total).
        final DiskPrivacy privacy = new DiskPrivacy(500);
        assertEquals(3L, privacy.publicShareOf(7L));
        assertEquals(4L, privacy.privateShareOf(7L));
    }

    @Test
    void shares_alwaysReconstructTheTotal() {
        final DiskPrivacy privacy = new DiskPrivacy(333);
        final long total = 9_999L;
        assertEquals(total, privacy.publicShareOf(total) + privacy.privateShareOf(total));
    }

    @Test
    void publicShareOf_zeroTotalIsZero() {
        assertEquals(0L, new DiskPrivacy(700).publicShareOf(0L));
        assertEquals(0L, new DiskPrivacy(700).privateShareOf(0L));
    }

    @Test
    void publicShareOf_doesNotOverflowOnLargeWeight() {
        // A weight near the long range times 1000 must not overflow into a negative or wrong share.
        final DiskPrivacy privacy = new DiskPrivacy(250);
        final long total = 8_000_000_000L;
        assertEquals(2_000_000_000L, privacy.publicShareOf(total));
    }
}
