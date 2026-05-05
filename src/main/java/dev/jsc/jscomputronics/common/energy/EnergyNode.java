/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.energy;

import dev.jsc.jscomputronics.common.energy.internal.EnergyNetwork;

/**
 * A node of an energy network: generator, consumer or storage.
 */
public interface EnergyNode {

    EnergyNodeRole role();

    long supply();

    long demand();

    void onSupplied(long amount);

    void onConsumed(long amount);
}
