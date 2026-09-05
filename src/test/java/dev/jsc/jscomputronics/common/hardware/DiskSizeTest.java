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
    void capacityItems_oneTerabyteIs4096Items() {
        // 1 TB = 1,048,576 MB / 256 MB per item = 4,096 items.
        assertEquals(4_096L, DiskSize.TB_1.capacityItems());
    }

    @Test
    void capacityItems_smallestIs2000Items() {
        // 500 GB = 512,000 MB / 256 MB = 2,000 items.
        assertEquals(2_000L, DiskSize.GB_500.capacityItems());
    }

    @Test
    void capacityItems_doublesWithSize() {
        assertEquals(DiskSize.TB_1.capacityItems() * 2, DiskSize.TB_2.capacityItems());
        assertEquals(DiskSize.TB_2.capacityItems() * 2, DiskSize.TB_4.capacityItems());
        assertEquals(DiskSize.TB_4.capacityItems() * 2, DiskSize.TB_8.capacityItems());
    }

    @Test
    void capacityMb_matchesDiskSpec() {
        // A disk built from a size yields capacityMb = items × 256, regardless of tier.
        final DiskSpec hdd = new DiskSpec(StorageTier.HDD, dev.jsc.jscomputronics.common.tier.HardwareEra.STANDARD,
                DiskSize.TB_1.capacityItems(), 6);
        final DiskSpec nvme = new DiskSpec(StorageTier.NVME, dev.jsc.jscomputronics.common.tier.HardwareEra.STANDARD,
                DiskSize.TB_1.capacityItems(), 5);
        assertEquals(hdd.capacityMb(), nvme.capacityMb());
        assertEquals(1_048_576L, hdd.capacityMb());
    }
}
