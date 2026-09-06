/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.config;

import dev.jstech.core.operation.OperationBalance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreConfigKeysTest {

    @AfterEach
    void restoreDefaults() {
        OperationBalance.reset();
    }

    @Test
    void registry_whitelistsEveryBalanceKey() {
        final CoreConfigRegistry registry = CoreConfigKeys.registry();
        assertEquals(7, registry.size());
        for (final String path : new String[] {
                "balance.hdd_latency_ticks", "balance.ssd_latency_ticks", "balance.nvme_latency_ticks",
                "balance.operation_waiting_timeout_ticks", "balance.operation_priority_aging_ticks",
                "balance.subframe_efficiency_factor", "balance.orphaned_operations_expiry_hours"}) {
            assertTrue(registry.isWhitelisted(path), path + " must be whitelisted");
        }
    }

    @Test
    void keys_defaultToTheBalanceDefaults() {
        assertEquals(OperationBalance.DEFAULT_HDD_LATENCY_TICKS, CoreConfigKeys.HDD_LATENCY_TICKS.defaultValue());
        assertEquals(OperationBalance.DEFAULT_WAITING_TIMEOUT_TICKS,
                CoreConfigKeys.OPERATION_WAITING_TIMEOUT_TICKS.defaultValue());
        assertEquals(OperationBalance.DEFAULT_SUBFRAME_EFFICIENCY_FACTOR,
                CoreConfigKeys.SUBFRAME_EFFICIENCY_FACTOR.defaultValue());
        assertEquals(OperationBalance.DEFAULT_ORPHANED_OPERATIONS_EXPIRY_HOURS,
                CoreConfigKeys.ORPHANED_OPERATIONS_EXPIRY_HOURS.defaultValue());
    }

    @Test
    void validate_clampsAnOutOfRangeLatency() {
        final ConfigValidator validator = new ConfigValidator(ConfigLogger.NOOP);
        final ConfigValidationResult<Integer> result = validator.validate(CoreConfigKeys.HDD_LATENCY_TICKS, 5000);
        assertInstanceOf(ConfigValidationResult.Clamped.class, result);
        assertEquals(200, result.value());
    }

    @Test
    void validate_rejectsAWrongTypeToTheDefault() {
        final ConfigValidator validator = new ConfigValidator(ConfigLogger.NOOP);
        final ConfigValidationResult<Double> result =
                validator.validate(CoreConfigKeys.SUBFRAME_EFFICIENCY_FACTOR, "fast");
        assertInstanceOf(ConfigValidationResult.Rejected.class, result);
        assertEquals(0.6, result.value());
    }

    @Test
    void apply_pushesEachKeyIntoTheBalance() {
        CoreConfigKeys.apply(CoreConfigKeys.HDD_LATENCY_TICKS, 15);
        CoreConfigKeys.apply(CoreConfigKeys.SSD_LATENCY_TICKS, 4);
        CoreConfigKeys.apply(CoreConfigKeys.NVME_LATENCY_TICKS, 2);
        CoreConfigKeys.apply(CoreConfigKeys.OPERATION_WAITING_TIMEOUT_TICKS, 600);
        CoreConfigKeys.apply(CoreConfigKeys.OPERATION_PRIORITY_AGING_TICKS, 100);
        CoreConfigKeys.apply(CoreConfigKeys.SUBFRAME_EFFICIENCY_FACTOR, 0.8);
        CoreConfigKeys.apply(CoreConfigKeys.ORPHANED_OPERATIONS_EXPIRY_HOURS, 2);
        assertEquals(15, OperationBalance.hddLatencyTicks());
        assertEquals(4, OperationBalance.ssdLatencyTicks());
        assertEquals(2, OperationBalance.nvmeLatencyTicks());
        assertEquals(600, OperationBalance.waitingTimeoutTicks());
        assertEquals(100, OperationBalance.priorityAgingTicks());
        assertEquals(0.8, OperationBalance.subframeEfficiencyFactor());
        assertEquals(2 * OperationBalance.TICKS_PER_HOUR, OperationBalance.orphanedOperationsExpiryTicks());
    }
}
