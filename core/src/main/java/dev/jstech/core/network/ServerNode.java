/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network;

import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;

import java.util.Objects;

/**
 * Snapshot of a Server item operating inside a Server Rack. Its storage is counted in items, the unit every
 * disk shares whatever era it was made for; megabytes differ by era and stay a display concern.
 */
public record ServerNode(
        NodeUuid nodeUuid,
        NetworkUuid networkUuid,
        long storageItems
) implements IServiceNode{
    public ServerNode {
        Objects.requireNonNull(nodeUuid, "nodeUuid must not be null");
        Objects.requireNonNull(networkUuid, "networkUuid must not be null");
        if (storageItems < 0) {
            throw new IllegalArgumentException(
                    "storageItems must be >= 0; got " + storageItems);
        }
    }

    @Override
    public NetworkCategory category() {
        return NetworkCategory.C;
    }
}
