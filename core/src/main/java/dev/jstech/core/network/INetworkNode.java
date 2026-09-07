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
 * Sealed root for any participant of the data network with a persistent identity (Category B and C nodes).
 */
public sealed interface INetworkNode permits IComputerNode, IServiceNode{

    NodeUuid nodeUuid();

    NetworkUuid networkUuid();

    NetworkCategory category();
}
