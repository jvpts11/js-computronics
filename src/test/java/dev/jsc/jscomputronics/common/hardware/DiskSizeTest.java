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

class DiskSizeTest {

    @Test
    void capacityItems_oneTerabyteIs262144Items() {
        // 1 TB = 1,048,576 MB / 4 MB per item = 262,144 items.
        assertEquals(262_144L, DiskSize.TB_1.capacityItems());
    }

    @Test
    void capacityItems_smallestIs50Items() {
        // 200 MB / 4 MB = 50 items.
        assertEquals(50L, DiskSize.MB_200.capacityItems());
    }

    @Test
    void capacityItems_doublesWithSize() {
        assertEquals(DiskSize.TB_1.capacityItems() * 2, DiskSize.TB_2.capacityItems());
        assertEquals(DiskSize.TB_2.capacityItems() * 2, DiskSize.TB_4.capacityItems());
        assertEquals(DiskSize.TB_4.capacityItems() * 2, DiskSize.TB_8.capacityItems());
    }

    @Test
    void capacityMb_matchesDiskSpec() {
        // A disk built from a size yields capacityMb = items × 4, regardless of tier.
        final DiskSpec hdd = new DiskSpec(StorageTier.HDD, DiskSize.TB_1.capacityItems(), 6);
        final DiskSpec nvme = new DiskSpec(StorageTier.NVME, DiskSize.TB_1.capacityItems(), 5);
        assertEquals(hdd.capacityMb(), nvme.capacityMb());
        assertEquals(1_048_576L, hdd.capacityMb());
    }
}
