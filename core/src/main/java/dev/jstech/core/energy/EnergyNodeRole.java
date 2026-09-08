/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.energy;

/**
 * Role of an {@link IEnergyNode} in the energy network.
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
