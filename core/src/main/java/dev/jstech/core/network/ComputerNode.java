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

/**
 * Sealed root for "computer-shaped" nodes in the network — Mainframes and Subframes.
 */
public sealed interface ComputerNode extends INetworkNode permits MainframeNode, SubframeNode {

    NodeUuid nodeUuid();

    NetworkUuid networkUuid();

    long contributedCapacity();
}