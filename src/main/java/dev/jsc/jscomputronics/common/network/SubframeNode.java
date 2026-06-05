/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;

import java.util.Objects;

/**
 * Snapshot of a Subframe BlockEntity, as used by the {@link NetworkSystem}.
 */
public record SubframeNode(
        NodeUuid nodeUuid,
        NetworkUuid networkUuid,
        long ownCapacity,
        java.util.Optional<NodeUuid> orchestratingMainframeUuid
) implements ComputerNode {
    public static final double CONTRIBUTION_FACTOR = 0.6;

    public SubframeNode {
        Objects.requireNonNull(nodeUuid, "nodeUuid must not be null");
        Objects.requireNonNull(networkUuid, "networkUuid must not be null");
        Objects.requireNonNull(orchestratingMainframeUuid,
                "orchestratingMainframeUuid must not be null (use Optional.empty for idle)");

        if (ownCapacity < 0) {
            throw new IllegalArgumentException(
                    "ownCapacity must be >= 0; got " + ownCapacity);
        }
    }

    @Override
    public long contributedCapacity() {
        if (orchestratingMainframeUuid.isEmpty()) {
            return 0; // Idle subframe contributes nothing.
        }
        return Math.round(ownCapacity * CONTRIBUTION_FACTOR);
    }

    @Override
    public NetworkCategory category() {
        return NetworkCategory.C;
    }
}
