/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.energy;

/**
 * Role of an {@link EnergyNode} in the energy network.
 */
public enum EnergyNodeRole {

    GENERATOR,
    CONSUMER,
    STORAGE;

    public boolean canSupply() {
        return this == GENERATOR || this == STORAGE;
    }

    public boolean canConsume() {
        return this == CONSUMER || this == STORAGE;
    }
}
