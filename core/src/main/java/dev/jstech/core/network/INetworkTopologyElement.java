/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network;

import dev.jstech.core.uuid.NetworkUuid;

/**
 * Sealed root for topology elements of the data network.
 */
public sealed interface INetworkTopologyElement permits ServerRouterElement {

    NetworkUuid networkUuid();

    long pos();
}
