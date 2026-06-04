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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRegistryStateTest {

    private static NetworkUuid net() {
        return new NetworkUuid(UUID.randomUUID());
    }

    @Test
    void empty_hasNoNetworks() {
        NetworkRegistryState state = NetworkRegistryState.empty();
        assertTrue(state.isEmpty());
        assertEquals(0, state.size());
    }

    @Test
    void withNetwork_addsNetwork() {
        NetworkUuid uuid = net();
        NetworkRegistryState state = NetworkRegistryState.empty().withNetwork(uuid);
        assertTrue(state.contains(uuid));
        assertEquals(1, state.size());
    }

    @Test
    void withNetwork_isImmutable_originalUnchanged() {
        NetworkRegistryState original = NetworkRegistryState.empty();
        NetworkUuid uuid = net();
        NetworkRegistryState modified = original.withNetwork(uuid);

        assertTrue(original.isEmpty());
        assertEquals(1, modified.size());
    }

    @Test
    void withNetwork_idempotent_returnsSameInstance() {
        NetworkUuid uuid = net();
        NetworkRegistryState state = NetworkRegistryState.empty().withNetwork(uuid);
        NetworkRegistryState again = state.withNetwork(uuid);
        // Idempotent add returns the same instance (no-op optimization).
        assertSame(state, again);
    }

    @Test
    void withoutNetwork_removesNetwork() {
        NetworkUuid uuid = net();
        NetworkRegistryState state = NetworkRegistryState.empty()
                .withNetwork(uuid)
                .withoutNetwork(uuid);
        assertFalse(state.contains(uuid));
        assertTrue(state.isEmpty());
    }

    @Test
    void withoutNetwork_absent_returnsSameInstance() {
        NetworkRegistryState state = NetworkRegistryState.empty();
        NetworkRegistryState again = state.withoutNetwork(net());
        assertSame(state, again);
    }

    @Test
    void of_buildsFromSet() {
        NetworkUuid a = net();
        NetworkUuid b = net();
        Set<NetworkUuid> set = new LinkedHashSet<>(Set.of(a, b));
        NetworkRegistryState state = NetworkRegistryState.of(set);
        assertEquals(2, state.size());
        assertTrue(state.contains(a));
        assertTrue(state.contains(b));
    }

    @Test
    void of_defensiveCopy_externalMutationDoesNotAffectState() {
        NetworkUuid a = net();
        Set<NetworkUuid> set = new LinkedHashSet<>();
        set.add(a);
        NetworkRegistryState state = NetworkRegistryState.of(set);

        set.add(net()); // mutate the source set after construction
        assertEquals(1, state.size(), "state must not reflect external mutation");
    }

    @Test
    void networks_returnedSetIsImmutable() {
        NetworkRegistryState state = NetworkRegistryState.empty().withNetwork(net());
        assertThrows(UnsupportedOperationException.class,
                () -> state.networks().add(net()));
    }

    @Test
    void of_nullSet_throws() {
        assertThrows(NullPointerException.class, () -> NetworkRegistryState.of(null));
    }

    @Test
    void equals_structural() {
        NetworkUuid a = net();
        NetworkRegistryState s1 = NetworkRegistryState.empty().withNetwork(a);
        NetworkRegistryState s2 = NetworkRegistryState.empty().withNetwork(a);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void equals_differentContents_notEqual() {
        NetworkRegistryState s1 = NetworkRegistryState.empty().withNetwork(net());
        NetworkRegistryState s2 = NetworkRegistryState.empty().withNetwork(net());
        assertFalse(s1.equals(s2));
    }

    @Test
    void multipleNetworks_preserveInsertionOrder() {
        NetworkUuid a = net();
        NetworkUuid b = net();
        NetworkUuid c = net();
        NetworkRegistryState state = NetworkRegistryState.empty()
                .withNetwork(a).withNetwork(b).withNetwork(c);

        assertEquals(3, state.size());
        var iterator = state.networks().iterator();
        assertEquals(a, iterator.next());
        assertEquals(b, iterator.next());
        assertEquals(c, iterator.next());
    }
}
