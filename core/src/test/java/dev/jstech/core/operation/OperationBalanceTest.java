/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.operation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationBalanceTest {

    @AfterEach
    void restoreDefaults() {
        OperationBalance.reset();
    }

    @Test
    void defaults_matchTheDesignEstimates() {
        assertEquals(10, OperationBalance.hddLatencyTicks());
        assertEquals(3, OperationBalance.ssdLatencyTicks());
        assertEquals(1, OperationBalance.nvmeLatencyTicks());
        assertEquals(1200, OperationBalance.waitingTimeoutTicks());
        assertEquals(600, OperationBalance.priorityAgingTicks());
        assertEquals(0.6, OperationBalance.subframeEfficiencyFactor());
        assertEquals(24 * OperationBalance.TICKS_PER_HOUR, OperationBalance.orphanedOperationsExpiryTicks());
    }

    @Test
    void latencies_neverGoNegative() {
        OperationBalance.setHddLatencyTicks(-4);
        OperationBalance.setSsdLatencyTicks(-4);
        OperationBalance.setNvmeLatencyTicks(-4);
        assertEquals(0, OperationBalance.hddLatencyTicks());
        assertEquals(0, OperationBalance.ssdLatencyTicks());
        assertEquals(0, OperationBalance.nvmeLatencyTicks());
    }

    @Test
    void waitingTimeout_isAtLeastOneTick() {
        OperationBalance.setWaitingTimeoutTicks(0);
        assertEquals(1, OperationBalance.waitingTimeoutTicks());
        OperationBalance.setWaitingTimeoutTicks(2400);
        assertEquals(2400, OperationBalance.waitingTimeoutTicks());
    }

    @Test
    void priorityAging_zeroDisablesAndNegativeClampsToZero() {
        OperationBalance.setPriorityAgingTicks(-1);
        assertEquals(0, OperationBalance.priorityAgingTicks());
        OperationBalance.setPriorityAgingTicks(300);
        assertEquals(300, OperationBalance.priorityAgingTicks());
    }

    @Test
    void subframeFactor_isClampedIntoTheUnitInterval() {
        OperationBalance.setSubframeEfficiencyFactor(1.7);
        assertEquals(1.0, OperationBalance.subframeEfficiencyFactor());
        OperationBalance.setSubframeEfficiencyFactor(-0.2);
        assertEquals(0.0, OperationBalance.subframeEfficiencyFactor());
        OperationBalance.setSubframeEfficiencyFactor(Double.NaN);
        assertEquals(0.6, OperationBalance.subframeEfficiencyFactor());
        OperationBalance.setSubframeEfficiencyFactor(0.75);
        assertEquals(0.75, OperationBalance.subframeEfficiencyFactor());
    }

    @Test
    void setOrphanedOperationsExpiryHours_convertsToTicks() {
        OperationBalance.setOrphanedOperationsExpiryHours(2);
        assertEquals(2 * OperationBalance.TICKS_PER_HOUR, OperationBalance.orphanedOperationsExpiryTicks());
    }

    @Test
    void setOrphanedOperationsExpiryHours_nonPositiveDisablesTheExpiry() {
        OperationBalance.setOrphanedOperationsExpiryHours(0);
        assertEquals(0L, OperationBalance.orphanedOperationsExpiryTicks());
        OperationBalance.setOrphanedOperationsExpiryHours(-5);
        assertEquals(0L, OperationBalance.orphanedOperationsExpiryTicks());
    }

    @Test
    void setOrphanedOperationsExpiryTicks_clampsNegativeToZero() {
        OperationBalance.setOrphanedOperationsExpiryTicks(-1L);
        assertEquals(0L, OperationBalance.orphanedOperationsExpiryTicks());
        OperationBalance.setOrphanedOperationsExpiryTicks(7L);
        assertEquals(7L, OperationBalance.orphanedOperationsExpiryTicks());
    }

    @Test
    void reset_restoresEveryDefault() {
        OperationBalance.setHddLatencyTicks(99);
        OperationBalance.setWaitingTimeoutTicks(5);
        OperationBalance.setSubframeEfficiencyFactor(0.1);
        OperationBalance.setOrphanedOperationsExpiryTicks(1L);
        OperationBalance.reset();
        assertEquals(10, OperationBalance.hddLatencyTicks());
        assertEquals(1200, OperationBalance.waitingTimeoutTicks());
        assertEquals(0.6, OperationBalance.subframeEfficiencyFactor());
        assertEquals(24 * OperationBalance.TICKS_PER_HOUR, OperationBalance.orphanedOperationsExpiryTicks());
    }
}
