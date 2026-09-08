/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.energy;

/**
 * Canonical cable tiers for the energy network.
 */
public enum EnergyTier {

    T1_COPPER(500L, "insulated_copper_cable"),
    T2_ALUMINUM(2_000L, "aluminum_electrode_cable"),
    T3_HIGH_CAPACITY(8_000L, "high_capacity_cable"),
    T4_SUPERCONDUCTOR_LT(32_000L, "low_temp_superconductor_cable"),
    T5_SUPERCONDUCTOR_HT(128_000L, "high_temp_superconductor_cable"),
    T6_QUANTUM(512_000L, "quantum_energy_cable"),
    T7_SINGULARITY(Long.MAX_VALUE, "singularity_cable");

    private final long maxThroughput;
    private final String translationKey;

    EnergyTier(final long maxThroughput, final String translationKey) {
        this.maxThroughput = maxThroughput;
        this.translationKey = translationKey;
    }

    public long maxThroughput() {
        return maxThroughput;
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean isUnlimited() {
        return this == T7_SINGULARITY;
    }
}
