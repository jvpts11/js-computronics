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
 * Sealed root for any participant of the data network with a persistent identity (Category B and C nodes).
 */
public sealed interface INetworkNode permits ComputerNode, ServiceNode{

    NodeUuid nodeUuid();

    NetworkUuid networkUuid();

    NetworkCategory category();
}
