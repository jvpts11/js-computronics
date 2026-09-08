/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.network;

import dev.jstech.core.uuid.NetworkUuid;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One kind of node, per network, keyed by whatever identifies a node of that kind (a node id, a position).
 * Every block entity re-registers its node on every tick, and every reader asks for a network's list
 * several times a tick, so both sides are made cheap: a registration is one map write, re-registering an
 * equal node is a no-op, and {@link #of} hands out the same immutable list until the roster changes. The
 * list a caller receives is a snapshot: a node that leaves after the call stays in that caller's list.
 *
 * @param <K> what identifies a node within its network
 * @param <N> the node snapshot
 */
final class NodeRegistry<K, N> {

    private final Map<NetworkUuid, LinkedHashMap<K, N>> byNetwork = new HashMap<>();
    private final Map<NetworkUuid, List<N>> snapshots = new HashMap<>();

    /** Adds or replaces the node under {@code key}; an equal node leaves the network's snapshot as it is. */
    void register(final NetworkUuid network, final K key, final N node) {
        Objects.requireNonNull(network, "network must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(node, "node must not be null");
        final N previous = byNetwork.computeIfAbsent(network, n -> new LinkedHashMap<>()).put(key, node);
        if (!node.equals(previous)) {
            snapshots.remove(network);
        }
    }

    void unregister(final NetworkUuid network, final K key) {
        final LinkedHashMap<K, N> nodes = byNetwork.get(network);
        if (nodes == null || nodes.remove(key) == null) {
            return;
        }
        if (nodes.isEmpty()) {
            byNetwork.remove(network);
        }
        snapshots.remove(network);
    }

    /** The network's nodes in registration order, as an immutable list shared until the roster changes. */
    List<N> of(final NetworkUuid network) {
        final LinkedHashMap<K, N> nodes = byNetwork.get(network);
        if (nodes == null) {
            return List.of();
        }
        return snapshots.computeIfAbsent(network, n -> List.copyOf(nodes.values()));
    }

    void clear() {
        byNetwork.clear();
        snapshots.clear();
    }
}
