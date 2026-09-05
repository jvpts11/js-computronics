/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.persistence;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JscChunkDataTest {

    private static NetworkUuid net() {
        return new NetworkUuid(UUID.randomUUID());
    }

    @Test
    void empty_hasNoNetworks() {
        JscChunkData data = JscChunkData.empty();
        assertTrue(data.isEmpty());
        assertEquals(0, data.size());
    }

    @Test
    void withNetwork_addsNetwork() {
        NetworkUuid uuid = net();
        JscChunkData data = JscChunkData.empty().withNetwork(uuid);
        assertTrue(data.contains(uuid));
    }

    @Test
    void withNetwork_immutable() {
        JscChunkData original = JscChunkData.empty();
        JscChunkData modified = original.withNetwork(net());
        assertTrue(original.isEmpty());
        assertEquals(1, modified.size());
    }

    @Test
    void withNetwork_idempotent_sameInstance() {
        NetworkUuid uuid = net();
        JscChunkData data = JscChunkData.empty().withNetwork(uuid);
        assertSame(data, data.withNetwork(uuid));
    }

    @Test
    void withoutNetwork_removes() {
        NetworkUuid uuid = net();
        JscChunkData data = JscChunkData.empty().withNetwork(uuid).withoutNetwork(uuid);
        assertFalse(data.contains(uuid));
    }

    @Test
    void withoutNetwork_absent_sameInstance() {
        JscChunkData data = JscChunkData.empty();
        assertSame(data, data.withoutNetwork(net()));
    }

    @Test
    void of_defensiveCopy() {
        var set = new java.util.LinkedHashSet<NetworkUuid>();
        set.add(net());
        JscChunkData data = JscChunkData.of(set);
        set.add(net());
        assertEquals(1, data.size());
    }

    @Test
    void networks_immutable() {
        JscChunkData data = JscChunkData.empty().withNetwork(net());
        assertThrows(UnsupportedOperationException.class,
                () -> data.networks().add(net()));
    }

    @Test
    void equals_structural() {
        NetworkUuid a = net();
        assertEquals(
                JscChunkData.empty().withNetwork(a),
                JscChunkData.empty().withNetwork(a));
    }

    @Test
    void of_storesMultipleNetworksInOneChunk() {
        NetworkUuid a = net();
        NetworkUuid b = net();
        JscChunkData data = JscChunkData.of(Set.of(a, b));
        assertEquals(2, data.size());
        assertTrue(data.contains(a));
        assertTrue(data.contains(b));
    }
}
