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
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiskSpecTest {

    @Test
    void capacityMb_isItemsTimes256() {
        assertEquals(256_000L, new DiskSpec(StorageTier.SSD, 1_000, 3).capacityMb());
    }

    @Test
    void capacityMb_zeroForEmptyDisk() {
        assertEquals(0L, new DiskSpec(StorageTier.HDD, 0, 0).capacityMb());
    }

    @Test
    void constructor_rejectsNegativeCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiskSpec(StorageTier.NVME, -1, 4));
    }

    @Test
    void constructor_rejectsNegativeTdp() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiskSpec(StorageTier.NVME, 1_000, -5));
    }

    @Test
    void constructor_rejectsNullTier() {
        assertThrows(NullPointerException.class,
                () -> new DiskSpec(null, 1_000, 4));
    }
}
