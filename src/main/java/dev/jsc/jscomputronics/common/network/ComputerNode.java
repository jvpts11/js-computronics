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

/**
 * Sealed root for "computer-shaped" nodes in the network — Mainframes and Subframes.
 */
public sealed interface ComputerNode extends INetworkNode permits MainframeNode, SubframeNode {

    NodeUuid nodeUuid();

    NetworkUuid networkUuid();

    long contributedCapacity();
}