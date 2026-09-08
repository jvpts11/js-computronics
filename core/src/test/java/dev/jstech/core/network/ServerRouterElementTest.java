/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.network;

import dev.jstech.core.tier.IndustrialTier;
import dev.jstech.core.uuid.NetworkUuid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerRouterElementTest {

    @Test
    void maxRacksFor_followsTierBudget() {
        assertEquals(4, ServerRouterElement.maxRacksFor(IndustrialTier.T2));
        assertEquals(8, ServerRouterElement.maxRacksFor(IndustrialTier.T3));
        assertEquals(16, ServerRouterElement.maxRacksFor(IndustrialTier.T4));
        assertEquals(32, ServerRouterElement.maxRacksFor(IndustrialTier.T5));
    }

    @Test
    void maxRacksFor_outsideRouterBand_isZero() {
        assertEquals(0, ServerRouterElement.maxRacksFor(IndustrialTier.T1));
        assertEquals(0, ServerRouterElement.maxRacksFor(IndustrialTier.T6));
    }

    @Test
    void maxRacks_matchesTier() {
        final ServerRouterElement element =
                new ServerRouterElement(NetworkUuid.random(), 5L, IndustrialTier.T3);
        assertEquals(8, element.maxRacks());
    }

    @Test
    void constructor_rejectsNullNetwork() {
        assertThrows(NullPointerException.class,
                () -> new ServerRouterElement(null, 0L, IndustrialTier.T3));
    }

    @Test
    void constructor_rejectsNullTier() {
        assertThrows(NullPointerException.class,
                () -> new ServerRouterElement(NetworkUuid.random(), 0L, null));
    }
}
