/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.persistence;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of which networks exist in one dimension.
 */
public final class NetworkRegistryState {

    private final Set<NetworkUuid> networks;

    private NetworkRegistryState(final Set<NetworkUuid> networks) {
        // Defensive copy + unmodifiable wrapper; preserve insertion order.
        this.networks = Collections.unmodifiableSet(new LinkedHashSet<>(networks));
    }

    public static NetworkRegistryState empty() {
        return new NetworkRegistryState(new LinkedHashSet<>());
    }

    public static NetworkRegistryState of(final Set<NetworkUuid> networks) {
        Objects.requireNonNull(networks, "networks must not be null");
        for (final NetworkUuid uuid : networks) {
            Objects.requireNonNull(uuid, "network UUID must not be null");
        }
        return new NetworkRegistryState(networks);
    }

    public NetworkRegistryState withNetwork(final NetworkUuid uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        if (networks.contains(uuid)) {
            return this;
        }
        final Set<NetworkUuid> next = new LinkedHashSet<>(networks);
        next.add(uuid);
        return new NetworkRegistryState(next);
    }

    public NetworkRegistryState withoutNetwork(final NetworkUuid uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        if (!networks.contains(uuid)) {
            return this;
        }
        final Set<NetworkUuid> next = new LinkedHashSet<>(networks);
        next.remove(uuid);
        return new NetworkRegistryState(next);
    }

    public boolean contains(final NetworkUuid uuid) {
        return networks.contains(Objects.requireNonNull(uuid));
    }

    public int size() {
        return networks.size();
    }

    public boolean isEmpty() {
        return networks.isEmpty();
    }

    public Set<NetworkUuid> networks() {
        return networks;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NetworkRegistryState other)) {
            return false;
        }
        return networks.equals(other.networks);
    }

    @Override
    public int hashCode() {
        return networks.hashCode();
    }

    @Override
    public String toString() {
        return "NetworkRegistryState" + networks;
    }
}
