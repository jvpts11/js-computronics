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
 * Immutable per-chunk data: which networks have cables passing through this chunk.
 */
public final class JscChunkData {

    private final Set<NetworkUuid> networksInChunk;

    private JscChunkData(final Set<NetworkUuid> networksInChunk) {
        this.networksInChunk =
                Collections.unmodifiableSet(new LinkedHashSet<>(networksInChunk));
    }

    public static JscChunkData empty() {
        return new JscChunkData(new LinkedHashSet<>());
    }

    public static JscChunkData of(final Set<NetworkUuid> networks) {
        Objects.requireNonNull(networks, "networks must not be null");
        for (final NetworkUuid uuid : networks) {
            Objects.requireNonNull(uuid, "network UUID must not be null");
        }
        return new JscChunkData(networks);
    }

    public JscChunkData withNetwork(final NetworkUuid uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        if (networksInChunk.contains(uuid)) {
            return this;
        }
        final Set<NetworkUuid> next = new LinkedHashSet<>(networksInChunk);
        next.add(uuid);
        return new JscChunkData(next);
    }

    public JscChunkData withoutNetwork(final NetworkUuid uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        if (!networksInChunk.contains(uuid)) {
            return this;
        }
        final Set<NetworkUuid> next = new LinkedHashSet<>(networksInChunk);
        next.remove(uuid);
        return new JscChunkData(next);
    }

    public boolean contains(final NetworkUuid uuid) {
        return networksInChunk.contains(Objects.requireNonNull(uuid));
    }

    public boolean isEmpty() {
        return networksInChunk.isEmpty();
    }

    public int size() {
        return networksInChunk.size();
    }

    public Set<NetworkUuid> networks() {
        return networksInChunk;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JscChunkData other)) {
            return false;
        }
        return networksInChunk.equals(other.networksInChunk);
    }

    @Override
    public int hashCode() {
        return networksInChunk.hashCode();
    }

    @Override
    public String toString() {
        return "JscChunkData" + networksInChunk;
    }
}
