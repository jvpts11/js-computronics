/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.network.payload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PingDataTest {

    @Test
    void validTimestamp_isAccepted() {
        PingData data = new PingData(123456789L);
        assertEquals(123456789L, data.timestamp());
    }

    @Test
    void zeroTimestamp_isAccepted() {
        PingData data = new PingData(0L);
        assertEquals(0L, data.timestamp());
    }

    @Test
    void negativeTimestamp_throws() {
        assertThrows(IllegalArgumentException.class, () -> new PingData(-1L));
    }

    @Test
    void records_haveStructuralEquality() {
        assertEquals(new PingData(42L), new PingData(42L));
        assertEquals(new PingData(42L).hashCode(), new PingData(42L).hashCode());
    }

    @Test
    void differentTimestamps_notEqual() {
        assertNotEquals(new PingData(1L), new PingData(2L));
    }
}