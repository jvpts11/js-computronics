/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.network;

import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;

import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot of a Mainframe BlockEntity, as used by the {@link NetworkSystem}.
 */
public record MainframeNode(NodeUuid nodeUuid,
                               NetworkUuid networkUuid,
                               long ownCapacity,
                               FailoverRole failoverRole,
                               Optional<NodeUuid> failoverPartnerUuid,
                               long lastHeartbeatTick) implements IComputerNode{

    public MainframeNode {
        Objects.requireNonNull(nodeUuid, "nodeUuid must not be null");
        Objects.requireNonNull(networkUuid, "networkUuid must not be null");
        Objects.requireNonNull(failoverRole, "failoverRole must not be null");
        Objects.requireNonNull(failoverPartnerUuid, "failoverPartnerUuid must not be null");

        if (ownCapacity < 0) {
            throw new IllegalArgumentException(
                    "ownCapacity must be >= 0; got " + ownCapacity);
        }

        // Invariant: paired roles must have a partner; unpaired must not.
        if (failoverRole.isPaired() && failoverPartnerUuid.isEmpty()) {
            throw new IllegalArgumentException(
                    "Failover role " + failoverRole + " requires a partner UUID");
        }
        if (!failoverRole.isPaired() && failoverPartnerUuid.isPresent()) {
            throw new IllegalArgumentException(
                    "Failover role NONE must not have a partner UUID");
        }
    }

    @Override
    public long contributedCapacity() {
        /*
         * PASSIVE consumes 50% in standby; NOT contributed to total.
         * The function below returns the "net contribution" view.
         */
        if (failoverRole == FailoverRole.PASSIVE) {
            return 0; // Passive contributes nothing, it is pure overhead.
        }
        return ownCapacity;
    }

    @Override
    public NetworkCategory category() {
        return NetworkCategory.C;
    }
}
