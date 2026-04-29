/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.uuid;

import java.util.Objects;

/**
 * Combined identity and lifecycle of a network: a {@link NetworkUuid} paired with its current {@link NetworkUuidState}.
 */
public record NetworkUuidStatus(NetworkUuid uuid, NetworkUuidState state) {

    public NetworkUuidStatus {
        Objects.requireNonNull(uuid, "uuid must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }

    public static NetworkUuidStatus active(NetworkUuid uuid) {
        return new NetworkUuidStatus(uuid, NetworkUuidState.ACTIVE);
    }

    public static NetworkUuidStatus orphaned(NetworkUuid uuid) {
        return new NetworkUuidStatus(uuid, NetworkUuidState.ORPHANED);
    }

    public static NetworkUuidStatus conflicted(NetworkUuid uuid) {
        return new NetworkUuidStatus(uuid, NetworkUuidState.CONFLICTED);
    }

    public NetworkUuidStatus withState(NetworkUuidState newState) {
        return new NetworkUuidStatus(uuid, newState);
    }
}
