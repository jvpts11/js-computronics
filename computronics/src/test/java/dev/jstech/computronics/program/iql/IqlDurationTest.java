/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IqlDurationTest {

    @Test
    void toTicks_bareNumberIsSeconds() {
        assertEquals(600L, IqlDuration.toTicks("30"));
        assertEquals(600L, IqlDuration.toTicks("30s"));
    }

    @Test
    void toTicks_minutesAndHours() {
        assertEquals(6000L, IqlDuration.toTicks("5m"));
        assertEquals(72000L, IqlDuration.toTicks("1h"));
    }

    @Test
    void toTicks_rawTicks() {
        assertEquals(100L, IqlDuration.toTicks("100t"));
    }

    @Test
    void toTicks_rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> IqlDuration.toTicks("soon"));
        assertThrows(IllegalArgumentException.class, () -> IqlDuration.toTicks(""));
    }

    @Test
    void toTicks_rejectsUnknownUnit() {
        assertThrows(IllegalArgumentException.class, () -> IqlDuration.toTicks("30x"));
    }
}
