/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.energy.internal;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProportionalSplitterTest {

    @Test
    void zeroAvailable_returnsEmpty() {
        Map<Long, Long> result = ProportionalSplitter.split(
                0L,
                Map.of(1L, 100L, 2L, 100L),
                Map.of(1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE));
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyDemand_returnsEmpty() {
        Map<Long, Long> result = ProportionalSplitter.split(1000L, Map.of(), Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void abundantSupply_eachGetsFullDemand() {
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 200L);
        demand.put(2L, 300L);
        Map<Long, Long> cap = Map.of(1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(1000L, demand, cap);

        assertEquals(Long.valueOf(200L), result.get(1L));
        assertEquals(Long.valueOf(300L), result.get(2L));
    }

    @Test
    void exactSupply_eachGetsFullDemand() {
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 400L);
        demand.put(2L, 600L);
        Map<Long, Long> cap = Map.of(1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(1000L, demand, cap);

        assertEquals(Long.valueOf(400L), result.get(1L));
        assertEquals(Long.valueOf(600L), result.get(2L));
    }

    @Test
    void shortage_splitsProportionallyWithCleanRatio() {
        // available=1000, totalDemand=1200 → each gets 500
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 600L);
        demand.put(2L, 600L);
        Map<Long, Long> cap = Map.of(1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(1000L, demand, cap);

        assertEquals(Long.valueOf(500L), result.get(1L));
        assertEquals(Long.valueOf(500L), result.get(2L));
    }

    @Test
    void shortage_splitsProportionallyWithUnevenDemands() {
        // available=1000, totalDemand=1200
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 300L);
        demand.put(2L, 900L);
        Map<Long, Long> cap = Map.of(1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(1000L, demand, cap);

        assertEquals(Long.valueOf(250L), result.get(1L));
        assertEquals(Long.valueOf(750L), result.get(2L));
    }

    @Test
    void largestRemainder_distributesLeftoverWithoutDrift() {
        // available=10, totalDemand=300, three equal recipients.
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 100L);
        demand.put(2L, 100L);
        demand.put(3L, 100L);
        Map<Long, Long> cap = Map.of(
                1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE, 3L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(10L, demand, cap);

        long total = 0L;
        for (Long v : result.values()) {
            total += v;
        }
        assertEquals(10L, total);
        assertEquals(Long.valueOf(4L), result.get(1L));
        assertEquals(Long.valueOf(3L), result.get(2L));
        assertEquals(Long.valueOf(3L), result.get(3L));
    }

    @Test
    void cap_limitsRecipientIndependentlyOfSupply() {
        // cap[1] = 100 → max 100 even with demand 1000 and abundant supply
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 1000L);
        demand.put(2L, 1000L);
        Map<Long, Long> cap = Map.of(1L, 100L, 2L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(2000L, demand, cap);

        assertEquals(Long.valueOf(100L), result.get(1L));
        assertEquals(Long.valueOf(1000L), result.get(2L));
    }

    @Test
    void capZero_excludesRecipient() {
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 1000L);
        demand.put(2L, 1000L);
        Map<Long, Long> cap = Map.of(1L, 0L, 2L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(500L, demand, cap);

        assertFalse(result.containsKey(1L));
        assertEquals(Long.valueOf(500L), result.get(2L));
    }

    @Test
    void demandZero_excludesRecipient() {
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 0L);
        demand.put(2L, 100L);
        Map<Long, Long> cap = Map.of(1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(500L, demand, cap);

        assertFalse(result.containsKey(1L));
        assertEquals(Long.valueOf(100L), result.get(2L));
    }

    @Test
    void invariant_sumOfResultsAlwaysLessOrEqualAvailable() {
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 333L);
        demand.put(2L, 333L);
        demand.put(3L, 333L);
        demand.put(4L, 333L);
        demand.put(5L, 333L);
        Map<Long, Long> cap = Map.of(
                1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE,
                3L, Long.MAX_VALUE, 4L, Long.MAX_VALUE,
                5L, Long.MAX_VALUE);

        for (long avail = 1L; avail <= 1665L; avail += 7L) {
            Map<Long, Long> result = ProportionalSplitter.split(avail, demand, cap);
            long sum = 0L;
            for (Long v : result.values()) {
                sum += v;
            }
            assertTrue(sum <= avail,
                    "Sum should be <= available for avail=" + avail);
        }
    }

    @Test
    void invariant_noRecipientReceivesMoreThanCapOrDemand() {
        Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, 100L);
        demand.put(2L, 200L);
        demand.put(3L, 300L);
        Map<Long, Long> cap = Map.of(1L, 50L, 2L, 100L, 3L, Long.MAX_VALUE);

        Map<Long, Long> result = ProportionalSplitter.split(10000L, demand, cap);

        assertTrue(result.getOrDefault(1L, 0L) <= 50L);
        assertTrue(result.getOrDefault(2L, 0L) <= 100L);
        assertTrue(result.getOrDefault(3L, 0L) <= 300L);
    }

    @Test
    void shortage_conservesTheTotalWithoutOverflowOnHugeSupply() {
        // available * effDemand once overflowed a long here: 4e9 * 5e9 = 2e19 > Long.MAX (~9.2e18),
        // corrupting the largest-remainder split. With long-range FE this is a reachable late-tier case.
        final long demandEach = 5_000_000_000L;
        final long available = 4_000_000_000L;
        final Map<Long, Long> demand = new LinkedHashMap<>();
        demand.put(1L, demandEach);
        demand.put(2L, demandEach);
        final Map<Long, Long> cap = Map.of(1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE);

        final Map<Long, Long> result = ProportionalSplitter.split(available, demand, cap);

        long sum = 0L;
        for (final Long v : result.values()) {
            sum += v;
        }
        assertEquals(available, sum, "the split must conserve the available total exactly");
        assertEquals(Long.valueOf(available / 2L), result.get(1L));
        assertEquals(Long.valueOf(available / 2L), result.get(2L));
    }
}
