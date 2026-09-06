/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.capability;

import dev.jstech.core.network.NetworkCategory;

/**
 * Contract for a {@link NetworkCategory#C Category C} node that can DISPATCH Operations to the network.
 */
public interface IOperationSource extends INetworkNodeCapability{
    // Phase 1+: UUID dispatch(OperationType<?> type, OperationArgs args);
}
