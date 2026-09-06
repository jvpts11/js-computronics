/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary tests for the capacity-fraction split, run against the generic core with String keys so no Minecraft runtime is needed. Items weigh 1000 per unit and fluids weigh 1 per unit, mirroring {@link StorageKey#weight}; keys prefixed "fluid:" model a fluid.
 */
class DiskStorageViewTest {

    // 1000 per item-unit, 1 per fluid-unit — exactly what StorageKey.weight reports.
    private static final ToLongFunction<String> WEIGHT = key -> key.startsWith("fluid:") ? 1L : 1000L;

    // A capacity of 10 items = 10_000 weight (1000 per item).
    private static final long CAP_10_ITEMS = 10L * 1000L;

    private static Map<String, Long> contents(final Object... pairs) {
        final Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Long) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void publicView_zeroPermilleExposesNothing() {
        final Map<String, Long> c = contents("a", 5L);
        assertTrue(DiskStorageView.publicView(c, CAP_10_ITEMS, 0, WEIGHT).isEmpty());
        assertEquals(0L, DiskStorageView.publicWeight(c, CAP_10_ITEMS, 0, WEIGHT));
    }

    @Test
    void privateView_zeroPermilleHoldsEverything() {
        final Map<String, Long> c = contents("a", 5L);
        assertEquals(5L, DiskStorageView.privateView(c, CAP_10_ITEMS, 0, WEIGHT).get("a"));
    }

    @Test
    void publicView_thousandPermilleExposesEverythingHeld() {
        final Map<String, Long> c = contents("a", 4L, "b", 3L);
        final Map<String, Long> pub = DiskStorageView.publicView(c, CAP_10_ITEMS, 1000, WEIGHT);
        assertEquals(4L, pub.get("a"));
        assertEquals(3L, pub.get("b"));
        assertTrue(DiskStorageView.privateView(c, CAP_10_ITEMS, 1000, WEIGHT).isEmpty());
    }

    @Test
    void publicView_budgetWalksInsertionOrderAndSplitsTheStraddlingType() {
        // 50% of a 10-item disk = budget of 5 items (5000 weight). Walk a=3 (public, 3000 used), then
        // b=4 of which only 2 fit the remaining 2000 weight -> 2 public, 2 private; c entirely private.
        final Map<String, Long> c = contents("a", 3L, "b", 4L, "c", 6L);
        final Map<String, Long> pub = DiskStorageView.publicView(c, CAP_10_ITEMS, 500, WEIGHT);
        assertEquals(3L, pub.get("a"));
        assertEquals(2L, pub.get("b"));
        assertNull(pub.get("c"));

        final Map<String, Long> priv = DiskStorageView.privateView(c, CAP_10_ITEMS, 500, WEIGHT);
        assertNull(priv.get("a"));
        assertEquals(2L, priv.get("b"));
        assertEquals(6L, priv.get("c"));
    }

    @Test
    void publicView_exactBudgetTakesAWholeTypeAndStopsCleanly() {
        // 30% of 10 items = 3 items exactly: a=3 fills the budget and b stays entirely private.
        final Map<String, Long> c = contents("a", 3L, "b", 5L);
        final Map<String, Long> pub = DiskStorageView.publicView(c, CAP_10_ITEMS, 300, WEIGHT);
        assertEquals(3L, pub.get("a"));
        assertNull(pub.get("b"));
    }

    @Test
    void publicView_fluidWeighsOnePerMilliBucket() {
        // A fluid weighs 1 per unit; a 1-item-capacity disk (1000 weight) at 50% exposes 500 mB.
        final Map<String, Long> c = contents("fluid:water", 1000L);
        final Map<String, Long> pub = DiskStorageView.publicView(c, 1000L, 500, WEIGHT);
        assertEquals(500L, pub.get("fluid:water"));
        assertEquals(500L, DiskStorageView.privateView(c, 1000L, 500, WEIGHT).get("fluid:water"));
    }

    @Test
    void views_alwaysReconstructTheFullContents() {
        final Map<String, Long> c = contents("a", 3L, "b", 4L, "c", 6L);
        final Map<String, Long> pub = DiskStorageView.publicView(c, CAP_10_ITEMS, 370, WEIGHT);
        final Map<String, Long> priv = DiskStorageView.privateView(c, CAP_10_ITEMS, 370, WEIGHT);
        for (final String key : c.keySet()) {
            final long total = pub.getOrDefault(key, 0L) + priv.getOrDefault(key, 0L);
            assertEquals(c.get(key), total, "public + private must equal the held amount");
        }
    }

    @Test
    void publicWeight_neverExceedsTheBudget() {
        final Map<String, Long> c = contents("a", 9L);
        final long budget = DiskStorageView.publicBudget(CAP_10_ITEMS, 333);
        assertTrue(DiskStorageView.publicWeight(c, CAP_10_ITEMS, 333, WEIGHT) <= budget);
    }

    @Test
    void publicView_emptyCapacityExposesNothing() {
        final Map<String, Long> c = contents("a", 5L);
        assertTrue(DiskStorageView.publicView(c, 0L, 1000, WEIGHT).isEmpty());
    }
}
