/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network;

/**
 * Sealed root for non-orchestrator network nodes.
 */
public sealed interface IServiceNode extends INetworkNode permits ServerNode {
}
