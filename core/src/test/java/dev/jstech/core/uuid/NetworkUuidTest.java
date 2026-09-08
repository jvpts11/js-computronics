/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.uuid;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkUuidTest {
    @Test
    void random_producesNonNullUuid() {
        var uuid = NetworkUuid.random();
        assertNotNull(uuid);
        assertNotNull(uuid.value());
    }

    @Test
    void random_producesDistinctUuidsAcrossCalls() {
        var a = NetworkUuid.random();
        var b = NetworkUuid.random();
        assertNotEquals(a, b);
    }

    @Test
    void asString_roundTripsThroughFromString() {
        var original = NetworkUuid.random();
        var roundTripped = NetworkUuid.fromString(original.asString());
        assertEquals(original, roundTripped);
    }

    @Test
    void fromString_acceptsCanonicalForm() {
        var raw = UUID.randomUUID();
        var wrapped = NetworkUuid.fromString(raw.toString());
        assertEquals(raw, wrapped.value());
    }

    @Test
    void fromString_rejectsMalformedString() {
        assertThrows(IllegalArgumentException.class,
                () -> NetworkUuid.fromString("not-a-uuid"));
    }

    @Test
    void constructor_rejectsNullValue() {
        assertThrows(NullPointerException.class,
                () -> new NetworkUuid(null));
    }

    @Test
    void fromString_rejectsNullString() {
        assertThrows(NullPointerException.class,
                () -> NetworkUuid.fromString(null));
    }

    @Test
    void equality_isStructural() {
        var raw = UUID.randomUUID();
        var a = new NetworkUuid(raw);
        var b = new NetworkUuid(raw);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
