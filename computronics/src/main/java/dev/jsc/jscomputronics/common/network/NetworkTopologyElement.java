/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;

/**
 * Sealed root for topology elements of the data network.
 */
public sealed interface NetworkTopologyElement permits ServerRouterElement {

    NetworkUuid networkUuid();

    long pos();
}
