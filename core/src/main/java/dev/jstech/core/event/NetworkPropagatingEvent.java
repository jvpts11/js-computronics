/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.event;

import dev.jstech.core.uuid.NetworkUuid;

import java.util.Objects;

/**
 * Cancellable event fired when a network UUID is about to propagate through a newly-placed cable into adjacent components.
 */
public final class NetworkPropagatingEvent implements JscEvent.Cancellable{

    private final NetworkUuid networkUuid;
    private final long fromPos;
    private final long toPos;
    private boolean cancelled;

    public NetworkPropagatingEvent(
            final NetworkUuid networkUuid,
            final long fromPos,
            final long toPos) {
        this.networkUuid = Objects.requireNonNull(networkUuid,
                "networkUuid must not be null");
        this.fromPos = fromPos;
        this.toPos = toPos;
    }

    public NetworkUuid networkUuid() {
        return networkUuid;
    }

    public long fromPos() {
        return fromPos;
    }

    public long toPos() {
        return toPos;
    }

    @Override
    public String eventId() {
        return "network.propagated";
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}
