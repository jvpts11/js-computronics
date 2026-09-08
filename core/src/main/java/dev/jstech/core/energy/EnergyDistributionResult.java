/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.energy;

import dev.jstech.core.energy.internal.EnergyNetwork;

import java.util.Map;

/**
 * Immutable snapshot of the result of one {@link EnergyNetwork#tickDistribute()} call.
 */
public record EnergyDistributionResult(
        long totalSupply,
        long totalDemand,
        long totalDelivered,
        Map<Long, Long> perConsumerDelivered,
        Map<Long, Long> perCableUsage,
        long unsatisfiedDemand
) {
    public EnergyDistributionResult {
        if (totalSupply < 0 || totalDemand < 0 || totalDelivered < 0
                || unsatisfiedDemand < 0) {
            throw new IllegalArgumentException(
                    "Aggregate values cannot be negative");
        }
        perConsumerDelivered = Map.copyOf(perConsumerDelivered);
        perCableUsage = Map.copyOf(perCableUsage);
    }

    public static EnergyDistributionResult empty() {
        return new EnergyDistributionResult(
                0L, 0L, 0L, Map.of(), Map.of(), 0L);
    }

}
