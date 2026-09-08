/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.hardware;

import dev.jstech.core.operation.OperationBalance;

/**
 * The performance class of a disk. The seek latency is a balance value the server config owns; the rest
 * is fixed by the hardware.
 */
public enum StorageTier {

    HDD(1, 6, "Vaultis Keep HDD"),
    SSD(4, 3, "Vaultis Swift SSD"),
    NVME(16, 5, "Vaultis Bolt NVMe");

    private final int speedMultiplier;
    private final int tdpWatts;
    private final String productName;

    StorageTier(final int speedMultiplier, final int tdpWatts, final String productName) {
        this.speedMultiplier = speedMultiplier;
        this.tdpWatts = tdpWatts;
        this.productName = productName;
    }

    public int tdpWatts() {
        return tdpWatts;
    }

    public String productName() {
        return productName;
    }

    /** The seek latency in ticks before a SubOperation on this tier starts streaming, from the balance. */
    public int latencyTicks() {
        return switch (this) {
            case HDD -> OperationBalance.hddLatencyTicks();
            case SSD -> OperationBalance.ssdLatencyTicks();
            case NVME -> OperationBalance.nvmeLatencyTicks();
        };
    }

    public int speedMultiplier() {
        return speedMultiplier;
    }

    /** The faster of the two by hardware class, whatever the configured latencies happen to be. */
    public StorageTier faster(final StorageTier other) {
        return other.ordinal() > this.ordinal() ? other : this;
    }
}
