/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.persistence;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NetworkUuidState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of which networks exist in one dimension and the lifecycle state of each.
 */
public final class NetworkRegistryState {

    private final Map<NetworkUuid, NetworkUuidState> states;

    private NetworkRegistryState(final Map<NetworkUuid, NetworkUuidState> states) {
        // Defensive copy + unmodifiable wrapper; preserve insertion order.
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    public static NetworkRegistryState empty() {
        return new NetworkRegistryState(new LinkedHashMap<>());
    }

    public static NetworkRegistryState of(final Set<NetworkUuid> networks) {
        Objects.requireNonNull(networks, "networks must not be null");
        final Map<NetworkUuid, NetworkUuidState> map = new LinkedHashMap<>();
        for (final NetworkUuid uuid : networks) {
            Objects.requireNonNull(uuid, "network UUID must not be null");
            map.put(uuid, NetworkUuidState.ACTIVE);
        }
        return new NetworkRegistryState(map);
    }

    public static NetworkRegistryState ofStates(final Map<NetworkUuid, NetworkUuidState> states) {
        Objects.requireNonNull(states, "states must not be null");
        states.forEach((uuid, state) -> {
            Objects.requireNonNull(uuid, "network UUID must not be null");
            Objects.requireNonNull(state, "network state must not be null");
        });
        return new NetworkRegistryState(states);
    }

    public NetworkRegistryState withNetwork(final NetworkUuid uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        if (states.containsKey(uuid)) {
            return this;
        }
        final Map<NetworkUuid, NetworkUuidState> next = new LinkedHashMap<>(states);
        next.put(uuid, NetworkUuidState.ACTIVE);
        return new NetworkRegistryState(next);
    }

    public NetworkRegistryState withState(final NetworkUuid uuid, final NetworkUuidState state) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (state == states.get(uuid)) {
            return this;
        }
        final Map<NetworkUuid, NetworkUuidState> next = new LinkedHashMap<>(states);
        next.put(uuid, state);
        return new NetworkRegistryState(next);
    }

    public NetworkRegistryState withoutNetwork(final NetworkUuid uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        if (!states.containsKey(uuid)) {
            return this;
        }
        final Map<NetworkUuid, NetworkUuidState> next = new LinkedHashMap<>(states);
        next.remove(uuid);
        return new NetworkRegistryState(next);
    }

    public boolean contains(final NetworkUuid uuid) {
        return states.containsKey(Objects.requireNonNull(uuid));
    }

    public NetworkUuidState stateOf(final NetworkUuid uuid) {
        return states.get(Objects.requireNonNull(uuid));
    }

    public int size() {
        return states.size();
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }

    public Set<NetworkUuid> networks() {
        return states.keySet();
    }

    public Map<NetworkUuid, NetworkUuidState> states() {
        return states;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NetworkRegistryState other)) {
            return false;
        }
        return states.equals(other.states);
    }

    @Override
    public int hashCode() {
        return states.hashCode();
    }

    @Override
    public String toString() {
        return "NetworkRegistryState" + states;
    }
}
