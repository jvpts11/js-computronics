/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageTierTest {

    @Test
    void latencyTicks_lowerForFasterTier() {
        assertEquals(10, StorageTier.HDD.latencyTicks());
        assertEquals(3, StorageTier.SSD.latencyTicks());
        assertEquals(1, StorageTier.NVME.latencyTicks());
    }

    @Test
    void speedMultiplier_higherForFasterTier() {
        assertEquals(1, StorageTier.HDD.speedMultiplier());
        assertEquals(4, StorageTier.SSD.speedMultiplier());
        assertEquals(16, StorageTier.NVME.speedMultiplier());
    }

    @Test
    void faster_returnsLowerLatencyTier() {
        assertEquals(StorageTier.NVME, StorageTier.HDD.faster(StorageTier.NVME));
        assertEquals(StorageTier.SSD, StorageTier.HDD.faster(StorageTier.SSD));
        assertEquals(StorageTier.NVME, StorageTier.SSD.faster(StorageTier.NVME));
    }

    @Test
    void faster_isSymmetric() {
        assertEquals(StorageTier.NVME, StorageTier.NVME.faster(StorageTier.HDD));
        assertEquals(StorageTier.NVME, StorageTier.HDD.faster(StorageTier.NVME));
    }

    @Test
    void faster_sameTierReturnsItself() {
        assertEquals(StorageTier.SSD, StorageTier.SSD.faster(StorageTier.SSD));
    }
}
