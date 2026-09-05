/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.energy;

/**
 * An energy cable: limits the rate of flow between nodes of the network.
 */
public interface EnergyCable {

    EnergyTier tier();

    default long maxThroughput() {
        return tier().maxThroughput();
    }
}
