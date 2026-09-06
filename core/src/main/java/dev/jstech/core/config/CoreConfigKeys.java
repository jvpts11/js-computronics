/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.config;

import dev.jstech.core.operation.OperationBalance;

import java.util.List;

/**
 * The whitelist of server settings the series accepts, each with its default and the range it is clamped
 * into. A value outside its range is pulled to the nearest bound rather than refused, so a typo in the
 * file degrades a setting instead of breaking the world. The keys live under the {@code balance} table of
 * the server config.
 */
public final class CoreConfigKeys {

    private static final CoreConfigRegistry REGISTRY = new CoreConfigRegistry();

    public static final String BALANCE = "balance";

    private static final int MAX_LATENCY_TICKS = 200;
    private static final int MAX_TIMEOUT_TICKS = 72_000;
    private static final int MAX_EXPIRY_HOURS = 24 * 365;

    /** The seek latency of a hard disk drive, in ticks. */
    public static final ConfigKey<Integer> HDD_LATENCY_TICKS = REGISTRY.register(ConfigKey.ranged(
            List.of(BALANCE, "hdd_latency_ticks"), Integer.class, OperationBalance.DEFAULT_HDD_LATENCY_TICKS,
            new ConfigKeyRange<>(0, MAX_LATENCY_TICKS)));

    /** The seek latency of a solid-state drive, in ticks. */
    public static final ConfigKey<Integer> SSD_LATENCY_TICKS = REGISTRY.register(ConfigKey.ranged(
            List.of(BALANCE, "ssd_latency_ticks"), Integer.class, OperationBalance.DEFAULT_SSD_LATENCY_TICKS,
            new ConfigKeyRange<>(0, MAX_LATENCY_TICKS)));

    /** The seek latency of an NVMe drive, in ticks. */
    public static final ConfigKey<Integer> NVME_LATENCY_TICKS = REGISTRY.register(ConfigKey.ranged(
            List.of(BALANCE, "nvme_latency_ticks"), Integer.class, OperationBalance.DEFAULT_NVME_LATENCY_TICKS,
            new ConfigKeyRange<>(0, MAX_LATENCY_TICKS)));

    /** How long an Operation waits on a LOCKed resource or a busy executor before it gives up, in ticks. */
    public static final ConfigKey<Integer> OPERATION_WAITING_TIMEOUT_TICKS = REGISTRY.register(ConfigKey.ranged(
            List.of(BALANCE, "operation_waiting_timeout_ticks"), Integer.class,
            OperationBalance.DEFAULT_WAITING_TIMEOUT_TICKS, new ConfigKeyRange<>(20, MAX_TIMEOUT_TICKS)));

    /** Ticks a queued Operation waits per level of priority it gains; 0 disables aging. */
    public static final ConfigKey<Integer> OPERATION_PRIORITY_AGING_TICKS = REGISTRY.register(ConfigKey.ranged(
            List.of(BALANCE, "operation_priority_aging_ticks"), Integer.class,
            OperationBalance.DEFAULT_PRIORITY_AGING_TICKS, new ConfigKeyRange<>(0, MAX_TIMEOUT_TICKS)));

    /** The share of a Subframe's own capacity it lends to the Mainframe orchestrating it. */
    public static final ConfigKey<Double> SUBFRAME_EFFICIENCY_FACTOR = REGISTRY.register(ConfigKey.ranged(
            List.of(BALANCE, "subframe_efficiency_factor"), Double.class,
            OperationBalance.DEFAULT_SUBFRAME_EFFICIENCY_FACTOR, new ConfigKeyRange<>(0.0, 1.0)));

    /** Hours a persisted, never-resumed Operation may sit before it is discarded; 0 never expires. */
    public static final ConfigKey<Integer> ORPHANED_OPERATIONS_EXPIRY_HOURS = REGISTRY.register(ConfigKey.ranged(
            List.of(BALANCE, "orphaned_operations_expiry_hours"), Integer.class,
            OperationBalance.DEFAULT_ORPHANED_OPERATIONS_EXPIRY_HOURS, new ConfigKeyRange<>(0, MAX_EXPIRY_HOURS)));

    private CoreConfigKeys() {
    }

    public static CoreConfigRegistry registry() {
        return REGISTRY;
    }

    /**
     * Pushes one validated value into the runtime balance. Unknown paths are ignored: the registry is the
     * whitelist, so a value only reaches here through one of the keys above.
     */
    public static void apply(final ConfigKey<?> key, final Object value) {
        switch (key.dottedPath()) {
            case "balance.hdd_latency_ticks" -> OperationBalance.setHddLatencyTicks((Integer) value);
            case "balance.ssd_latency_ticks" -> OperationBalance.setSsdLatencyTicks((Integer) value);
            case "balance.nvme_latency_ticks" -> OperationBalance.setNvmeLatencyTicks((Integer) value);
            case "balance.operation_waiting_timeout_ticks" -> OperationBalance.setWaitingTimeoutTicks((Integer) value);
            case "balance.operation_priority_aging_ticks" -> OperationBalance.setPriorityAgingTicks((Integer) value);
            case "balance.subframe_efficiency_factor" -> OperationBalance.setSubframeEfficiencyFactor((Double) value);
            case "balance.orphaned_operations_expiry_hours" ->
                    OperationBalance.setOrphanedOperationsExpiryHours((Integer) value);
            default -> { }
        }
    }
}
