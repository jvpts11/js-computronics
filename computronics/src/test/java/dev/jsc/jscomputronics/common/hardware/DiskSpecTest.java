/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import dev.jstech.core.tier.HardwareEra;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiskSpecTest {

    @Test
    void capacityMb_isItemsTimes256OnAStandardDisk() {
        assertEquals(256_000L, new DiskSpec(StorageTier.SSD, HardwareEra.STANDARD, 1_000, 3).capacityMb());
    }

    @Test
    void capacityMb_followsTheDiskEra() {
        // The same 20 items are a 20 MB vintage drive, a 320 MB legacy one and a 5 GB standard one.
        assertEquals(20L, new DiskSpec(StorageTier.HDD, HardwareEra.VINTAGE, 20, 5).capacityMb());
        assertEquals(320L, new DiskSpec(StorageTier.HDD, HardwareEra.LEGACY, 20, 5).capacityMb());
        assertEquals(5_120L, new DiskSpec(StorageTier.HDD, HardwareEra.STANDARD, 20, 5).capacityMb());
        assertEquals(4_096L, new DiskSpec(StorageTier.HDD, HardwareEra.LEGACY, 256, 7).capacityMb());
    }

    @Test
    void capacityMb_zeroForEmptyDisk() {
        assertEquals(0L, new DiskSpec(StorageTier.HDD, HardwareEra.STANDARD, 0, 0).capacityMb());
    }

    @Test
    void sizeLabel_writesTheSizeTheWayADriveLabelDoes() {
        assertEquals("20 MB", DiskSpec.sizeLabel(20));
        assertEquals("1023 MB", DiskSpec.sizeLabel(1_023));
        assertEquals("4 GB", DiskSpec.sizeLabel(4_096));
        assertEquals("1.5 GB", DiskSpec.sizeLabel(1_536));
        assertEquals("500 GB", DiskSpec.sizeLabel(512_000));
        assertEquals("1 TB", DiskSpec.sizeLabel(1_048_576));
        assertEquals("8 TB", DiskSpec.sizeLabel(8L * 1_048_576L));
    }

    @Test
    void constructor_rejectsNegativeCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiskSpec(StorageTier.NVME, HardwareEra.STANDARD, -1, 4));
    }

    @Test
    void constructor_rejectsNegativeTdp() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiskSpec(StorageTier.NVME, HardwareEra.STANDARD, 1_000, -5));
    }

    @Test
    void constructor_rejectsNullTier() {
        assertThrows(NullPointerException.class,
                () -> new DiskSpec(null, HardwareEra.STANDARD, 1_000, 4));
    }

    @Test
    void constructor_rejectsNullEra() {
        assertThrows(NullPointerException.class,
                () -> new DiskSpec(StorageTier.NVME, null, 1_000, 4));
    }
}
