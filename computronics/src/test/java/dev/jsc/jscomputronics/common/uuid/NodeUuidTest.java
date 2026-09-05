/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.uuid;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeUuidTest {

    @Test
    void random_producesNonNullUuid() {
        var uuid = NodeUuid.random();
        assertNotNull(uuid);
        assertNotNull(uuid.value());
    }

    @Test
    void random_producesDistinctUuidsAcrossCalls() {
        var a = NodeUuid.random();
        var b = NodeUuid.random();
        assertNotEquals(a, b);
    }

    @Test
    void asString_roundTripsThroughFromString() {
        var original = NodeUuid.random();
        var roundTripped = NodeUuid.fromString(original.asString());
        assertEquals(original, roundTripped);
    }

    @Test
    void fromString_acceptsCanonicalForm() {
        var raw = UUID.randomUUID();
        var wrapped = NodeUuid.fromString(raw.toString());
        assertEquals(raw, wrapped.value());
    }

    @Test
    void fromString_rejectsMalformedString() {
        assertThrows(IllegalArgumentException.class,
                () -> NodeUuid.fromString("not-a-uuid"));
    }

    @Test
    void constructor_rejectsNullValue() {
        assertThrows(NullPointerException.class,
                () -> new NodeUuid(null));
    }

    @Test
    void equality_isStructural() {
        var raw = UUID.randomUUID();
        var a = new NodeUuid(raw);
        var b = new NodeUuid(raw);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
