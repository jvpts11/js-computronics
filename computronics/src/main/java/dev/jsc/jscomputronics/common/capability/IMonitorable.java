/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.capability;

import dev.jsc.jscomputronics.common.network.NetworkCategory;

/**
 * Contract for a {@link NetworkCategory#B Category B} node that can be INSPECTED (read-only) by the network.
 */
public interface IMonitorable extends INetworkNodeCapability{
    // Phase 1+: MonitorableSnapshot snapshot();
}
