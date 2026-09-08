/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.network;

import dev.jstech.core.uuid.NetworkUuid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRegistryTest {

    private record Node(String key, long value) {
    }

    private static NetworkUuid net() {
        return NetworkUuid.random();
    }

    @Test
    void of_isEmptyForAnUnknownNetwork() {
        assertTrue(new NodeRegistry<String, Node>().of(net()).isEmpty());
    }

    @Test
    void register_keepsRegistrationOrderAndReplacesByKey() {
        final NodeRegistry<String, Node> registry = new NodeRegistry<>();
        final NetworkUuid network = net();
        registry.register(network, "a", new Node("a", 1));
        registry.register(network, "b", new Node("b", 2));
        registry.register(network, "a", new Node("a", 3));
        assertEquals(List.of(new Node("a", 3), new Node("b", 2)), registry.of(network));
    }

    @Test
    void of_sharesTheSameListUntilTheRosterChanges() {
        final NodeRegistry<String, Node> registry = new NodeRegistry<>();
        final NetworkUuid network = net();
        registry.register(network, "a", new Node("a", 1));
        final List<Node> first = registry.of(network);
        // A rack re-registers an unchanged node every tick: that must not invalidate the readers' list.
        registry.register(network, "a", new Node("a", 1));
        assertSame(first, registry.of(network));
        registry.register(network, "a", new Node("a", 2));
        assertNotSame(first, registry.of(network));
    }

    @Test
    void of_returnsAnImmutableList() {
        final NodeRegistry<String, Node> registry = new NodeRegistry<>();
        final NetworkUuid network = net();
        registry.register(network, "a", new Node("a", 1));
        assertThrows(UnsupportedOperationException.class, () -> registry.of(network).add(new Node("z", 9)));
    }

    @Test
    void unregister_dropsTheNodeAndForgetsAnEmptyNetwork() {
        final NodeRegistry<String, Node> registry = new NodeRegistry<>();
        final NetworkUuid network = net();
        registry.register(network, "a", new Node("a", 1));
        registry.register(network, "b", new Node("b", 2));
        registry.unregister(network, "a");
        assertEquals(List.of(new Node("b", 2)), registry.of(network));
        registry.unregister(network, "b");
        assertTrue(registry.of(network).isEmpty());
        registry.unregister(network, "missing"); // a no-op, never an error
    }

    @Test
    void networks_areIsolated() {
        final NodeRegistry<String, Node> registry = new NodeRegistry<>();
        final NetworkUuid a = net();
        final NetworkUuid b = net();
        registry.register(a, "x", new Node("x", 1));
        registry.register(b, "x", new Node("x", 2));
        assertEquals(1L, registry.of(a).get(0).value());
        assertEquals(2L, registry.of(b).get(0).value());
    }

    @Test
    void clear_forgetsEverything() {
        final NodeRegistry<String, Node> registry = new NodeRegistry<>();
        final NetworkUuid network = net();
        registry.register(network, "a", new Node("a", 1));
        registry.clear();
        assertTrue(registry.of(network).isEmpty());
    }

    @Test
    void register_rejectsNulls() {
        final NodeRegistry<String, Node> registry = new NodeRegistry<>();
        assertThrows(NullPointerException.class, () -> registry.register(null, "a", new Node("a", 1)));
        assertThrows(NullPointerException.class, () -> registry.register(net(), null, new Node("a", 1)));
        assertThrows(NullPointerException.class, () -> registry.register(net(), "a", null));
    }
}
