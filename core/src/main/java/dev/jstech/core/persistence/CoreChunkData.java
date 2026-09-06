/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.persistence;

import dev.jstech.core.uuid.NetworkUuid;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable per-chunk data: which networks have cables passing through this chunk.
 */
public final class CoreChunkData {

    private final Set<NetworkUuid> networksInChunk;

    private CoreChunkData(final Set<NetworkUuid> networksInChunk) {
        this.networksInChunk =
                Collections.unmodifiableSet(new LinkedHashSet<>(networksInChunk));
    }

    public static CoreChunkData empty() {
        return new CoreChunkData(new LinkedHashSet<>());
    }

    public static CoreChunkData of(final Set<NetworkUuid> networks) {
        Objects.requireNonNull(networks, "networks must not be null");
        for (final NetworkUuid uuid : networks) {
            Objects.requireNonNull(uuid, "network UUID must not be null");
        }
        return new CoreChunkData(networks);
    }

    public CoreChunkData withNetwork(final NetworkUuid uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        if (networksInChunk.contains(uuid)) {
            return this;
        }
        final Set<NetworkUuid> next = new LinkedHashSet<>(networksInChunk);
        next.add(uuid);
        return new CoreChunkData(next);
    }

    public CoreChunkData withoutNetwork(final NetworkUuid uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        if (!networksInChunk.contains(uuid)) {
            return this;
        }
        final Set<NetworkUuid> next = new LinkedHashSet<>(networksInChunk);
        next.remove(uuid);
        return new CoreChunkData(next);
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
        if (!(o instanceof CoreChunkData other)) {
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
        return "CoreChunkData" + networksInChunk;
    }
}
