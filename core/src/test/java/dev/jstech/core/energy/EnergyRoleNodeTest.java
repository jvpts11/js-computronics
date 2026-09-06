/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyRoleNodeTest {

    @Test
    void generator_canSupplyButNotConsume() {
        assertTrue(EnergyNodeRole.GENERATOR.canSupply());
        assertFalse(EnergyNodeRole.GENERATOR.canConsume());
    }

    @Test
    void consumer_canConsumeButNotSupply() {
        assertFalse(EnergyNodeRole.CONSUMER.canSupply());
        assertTrue(EnergyNodeRole.CONSUMER.canConsume());
    }

    @Test
    void storage_canBothSupplyAndConsume() {
        assertTrue(EnergyNodeRole.STORAGE.canSupply());
        assertTrue(EnergyNodeRole.STORAGE.canConsume());
    }
}
