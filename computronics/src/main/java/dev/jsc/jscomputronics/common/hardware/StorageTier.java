/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

/**
 * The performance class of a disk.
 */
public enum StorageTier {

    HDD(10, 1, 6, "Vaultis Keep HDD"),
    SSD(3, 4, 3, "Vaultis Swift SSD"),
    NVME(1, 16, 5, "Vaultis Bolt NVMe");

    private final int latencyTicks;
    private final int speedMultiplier;
    private final int tdpWatts;
    private final String productName;

    StorageTier(final int latencyTicks, final int speedMultiplier,
                final int tdpWatts, final String productName) {
        this.latencyTicks = latencyTicks;
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

    public int latencyTicks() {
        return latencyTicks;
    }

    public int speedMultiplier() {
        return speedMultiplier;
    }

    public StorageTier faster(final StorageTier other) {
        return other.latencyTicks < this.latencyTicks ? other : this;
    }
}
