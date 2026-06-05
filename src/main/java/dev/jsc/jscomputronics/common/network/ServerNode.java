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
 * Snapshot of a Server item operating inside a Server Rack.
 */
public record ServerNode(
        NodeUuid nodeUuid,
        NetworkUuid networkUuid,
        long storageMB
) implements ServiceNode{
    public ServerNode {
        Objects.requireNonNull(nodeUuid, "nodeUuid must not be null");
        Objects.requireNonNull(networkUuid, "networkUuid must not be null");
        if (storageMB < 0) {
            throw new IllegalArgumentException(
                    "storageMB must be >= 0; got " + storageMB);
        }
    }

    @Override
    public NetworkCategory category() {
        return NetworkCategory.C;
    }
}
