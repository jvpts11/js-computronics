/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageAllocatorTest {

    private static final NodeUuid A = node(1);
    private static final NodeUuid B = node(2);
    private static final NodeUuid C = node(3);

    @Test
    void allocate_fillsFastestTierFirst() {
        final Allocation plan = StorageAllocator.allocate(List.of(
                new ItemLocation(A, StorageTier.HDD, 100),
                new ItemLocation(B, StorageTier.NVME, 100),
                new ItemLocation(C, StorageTier.SSD, 100)), 150);

        assertEquals(150L, plan.allocated());
        assertEquals(100L, plan.perServer().get(B), "NVMe drained first");
        assertEquals(50L, plan.perServer().get(C), "SSD next");
        assertNull(plan.perServer().get(A), "HDD untouched while faster tiers cover the demand");
        assertTrue(plan.covers(150));
    }

    @Test
    void allocate_largestHoldingFirstWithinTier() {
        final Allocation plan = StorageAllocator.allocate(List.of(
                new ItemLocation(A, StorageTier.SSD, 30),
                new ItemLocation(B, StorageTier.SSD, 70)), 50);

        assertEquals(50L, plan.perServer().get(B), "the bigger same-tier holding is drained first");
        assertNull(plan.perServer().get(A));
    }

    @Test
    void allocate_partialWhenInsufficient() {
        final Allocation plan = StorageAllocator.allocate(List.of(
                new ItemLocation(A, StorageTier.NVME, 40)), 100);

        assertEquals(40L, plan.allocated());
        assertFalse(plan.covers(100));
    }

    @Test
    void allocate_stopsWhenDemandMet() {
        final Allocation plan = StorageAllocator.allocate(List.of(
                new ItemLocation(A, StorageTier.NVME, 64),
                new ItemLocation(B, StorageTier.NVME, 64)), 64);

        assertEquals(1, plan.perServer().size(), "only the first source is touched");
        assertEquals(64L, plan.allocated());
    }

    @Test
    void allocate_skipsEmptyLocations() {
        final Allocation plan = StorageAllocator.allocate(List.of(
                new ItemLocation(A, StorageTier.NVME, 0),
                new ItemLocation(B, StorageTier.HDD, 20)), 20);

        assertEquals(20L, plan.perServer().get(B));
        assertNull(plan.perServer().get(A));
    }

    @Test
    void allocate_emptyForZeroDemand() {
        final Allocation plan = StorageAllocator.allocate(List.of(
                new ItemLocation(A, StorageTier.NVME, 100)), 0);

        assertTrue(plan.isEmpty());
        assertEquals(0L, plan.allocated());
    }

    private static NodeUuid node(final int id) {
        return new NodeUuid(new UUID(0L, id));
    }
}
