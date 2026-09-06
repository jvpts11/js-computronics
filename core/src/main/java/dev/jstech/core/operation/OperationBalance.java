/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.operation;

/**
 * The runtime balance values of the Operations engine, as one place every module reads them from. The
 * defaults are the design estimates; the server config overwrites them when it loads (and again on a
 * reload), so a pack author tunes the engine without touching code. Reads are lock-free volatile reads:
 * an Operation on a virtual thread may consult them at any time.
 */
public final class OperationBalance {

    public static final long TICKS_PER_HOUR = 72_000L;

    /** The seek latency of a hard disk drive, in ticks, before its SubOperation starts streaming. */
    public static final int DEFAULT_HDD_LATENCY_TICKS = 10;
    /** The seek latency of a solid-state drive, in ticks. */
    public static final int DEFAULT_SSD_LATENCY_TICKS = 3;
    /** The seek latency of an NVMe drive, in ticks. */
    public static final int DEFAULT_NVME_LATENCY_TICKS = 1;
    /** How long an Operation waits on a LOCKed resource or a busy executor before it gives up. */
    public static final int DEFAULT_WAITING_TIMEOUT_TICKS = 1200;
    /** Ticks a queued Operation waits per level of priority it gains; {@code 0} disables aging. */
    public static final int DEFAULT_PRIORITY_AGING_TICKS = 600;
    /** The share of a Subframe's own capacity it lends to the Mainframe orchestrating it. */
    public static final double DEFAULT_SUBFRAME_EFFICIENCY_FACTOR = 0.6;
    /** How long a persisted, never-resumed Operation may sit before it is discarded instead of resumed. */
    public static final int DEFAULT_ORPHANED_OPERATIONS_EXPIRY_HOURS = 24;

    private static volatile int hddLatencyTicks = DEFAULT_HDD_LATENCY_TICKS;
    private static volatile int ssdLatencyTicks = DEFAULT_SSD_LATENCY_TICKS;
    private static volatile int nvmeLatencyTicks = DEFAULT_NVME_LATENCY_TICKS;
    private static volatile int waitingTimeoutTicks = DEFAULT_WAITING_TIMEOUT_TICKS;
    private static volatile int priorityAgingTicks = DEFAULT_PRIORITY_AGING_TICKS;
    private static volatile double subframeEfficiencyFactor = DEFAULT_SUBFRAME_EFFICIENCY_FACTOR;
    private static volatile long orphanedOperationsExpiryTicks =
            DEFAULT_ORPHANED_OPERATIONS_EXPIRY_HOURS * TICKS_PER_HOUR;

    private OperationBalance() {
    }

    public static int hddLatencyTicks() {
        return hddLatencyTicks;
    }

    public static void setHddLatencyTicks(final int ticks) {
        hddLatencyTicks = Math.max(0, ticks);
    }

    public static int ssdLatencyTicks() {
        return ssdLatencyTicks;
    }

    public static void setSsdLatencyTicks(final int ticks) {
        ssdLatencyTicks = Math.max(0, ticks);
    }

    public static int nvmeLatencyTicks() {
        return nvmeLatencyTicks;
    }

    public static void setNvmeLatencyTicks(final int ticks) {
        nvmeLatencyTicks = Math.max(0, ticks);
    }

    /** The WAITING timeout in ticks; never below one tick, so a waiter always gets one retry. */
    public static int waitingTimeoutTicks() {
        return waitingTimeoutTicks;
    }

    public static void setWaitingTimeoutTicks(final int ticks) {
        waitingTimeoutTicks = Math.max(1, ticks);
    }

    /** Ticks of deferral per level of priority gained; {@code 0} means a queued Operation never ages up. */
    public static int priorityAgingTicks() {
        return priorityAgingTicks;
    }

    public static void setPriorityAgingTicks(final int ticks) {
        priorityAgingTicks = Math.max(0, ticks);
    }

    /** The share of its own capacity a Subframe contributes, between 0 and 1. */
    public static double subframeEfficiencyFactor() {
        return subframeEfficiencyFactor;
    }

    public static void setSubframeEfficiencyFactor(final double factor) {
        subframeEfficiencyFactor = Double.isNaN(factor) ? DEFAULT_SUBFRAME_EFFICIENCY_FACTOR
                : Math.max(0.0, Math.min(1.0, factor));
    }

    /** The orphan expiry in ticks; {@code 0} means persisted Operations never expire. */
    public static long orphanedOperationsExpiryTicks() {
        return orphanedOperationsExpiryTicks;
    }

    /** Sets the orphan expiry from the config's hours; a non-positive value disables the expiry. */
    public static void setOrphanedOperationsExpiryHours(final int hours) {
        orphanedOperationsExpiryTicks = hours <= 0 ? 0L : hours * TICKS_PER_HOUR;
    }

    /** Sets the orphan expiry directly in ticks (tests); a non-positive value disables the expiry. */
    public static void setOrphanedOperationsExpiryTicks(final long ticks) {
        orphanedOperationsExpiryTicks = Math.max(0L, ticks);
    }

    /** Restores every value to its default. */
    public static void reset() {
        hddLatencyTicks = DEFAULT_HDD_LATENCY_TICKS;
        ssdLatencyTicks = DEFAULT_SSD_LATENCY_TICKS;
        nvmeLatencyTicks = DEFAULT_NVME_LATENCY_TICKS;
        waitingTimeoutTicks = DEFAULT_WAITING_TIMEOUT_TICKS;
        priorityAgingTicks = DEFAULT_PRIORITY_AGING_TICKS;
        subframeEfficiencyFactor = DEFAULT_SUBFRAME_EFFICIENCY_FACTOR;
        orphanedOperationsExpiryTicks = DEFAULT_ORPHANED_OPERATIONS_EXPIRY_HOURS * TICKS_PER_HOUR;
    }
}
