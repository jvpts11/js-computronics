/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation.exec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EqualShareTest {

    @Test
    void split_dividesEvenlyWhenDivisible() {
        assertArrayEquals(new long[] {5, 5, 5}, EqualShare.split(15, 3));
    }

    @Test
    void split_givesTheRemainderToTheFirstShares() {
        assertArrayEquals(new long[] {4, 4, 3, 3}, EqualShare.split(14, 4));
    }

    @Test
    void split_sumsToExactlyTheTotal() {
        final long[] shares = EqualShare.split(1000, 7);
        long sum = 0L;
        for (final long share : shares) {
            sum += share;
        }
        assertEquals(1000L, sum, "no throughput is invented or lost to rounding");
    }

    @Test
    void split_starvesSomePartsWhenTotalBelowCount() {
        assertArrayEquals(new long[] {1, 1, 0, 0, 0}, EqualShare.split(2, 5),
                "an overloaded split leaves some parts with nothing, so the Mainframe throttles");
    }

    @Test
    void split_emptyForNoParts() {
        assertEquals(0, EqualShare.split(10, 0).length);
    }

    @Test
    void split_treatsNegativeTotalAsZero() {
        assertArrayEquals(new long[] {0, 0}, EqualShare.split(-5, 2));
    }
}
