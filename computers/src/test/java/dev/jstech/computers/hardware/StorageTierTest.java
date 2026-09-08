/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.hardware;

import dev.jstech.core.operation.OperationBalance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageTierTest {

    @AfterEach
    void restoreBalance() {
        OperationBalance.reset();
    }

    @Test
    void latencyTicks_lowerForFasterTier() {
        assertEquals(10, StorageTier.HDD.latencyTicks());
        assertEquals(3, StorageTier.SSD.latencyTicks());
        assertEquals(1, StorageTier.NVME.latencyTicks());
    }

    @Test
    void latencyTicks_followsTheBalance() {
        OperationBalance.setHddLatencyTicks(25);
        OperationBalance.setSsdLatencyTicks(7);
        OperationBalance.setNvmeLatencyTicks(0);
        assertEquals(25, StorageTier.HDD.latencyTicks());
        assertEquals(7, StorageTier.SSD.latencyTicks());
        assertEquals(0, StorageTier.NVME.latencyTicks());
    }

    @Test
    void faster_isByHardwareClassEvenWhenLatenciesAreTuned() {
        // A pack that makes SSD seek slower than HDD does not turn the HDD into the faster class.
        OperationBalance.setSsdLatencyTicks(30);
        assertEquals(StorageTier.SSD, StorageTier.HDD.faster(StorageTier.SSD));
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
