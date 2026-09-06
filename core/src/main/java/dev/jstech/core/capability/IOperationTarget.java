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
 * Contract for a {@link NetworkCategory#C Category C} node that can RECEIVE Operations from the Mainframe.
 */
public interface IOperationTarget extends INetworkNodeCapability{
    // Phase 1+: boolean canAccept(OperationType<?> type);
    // Phase 1+: void accept(Operation op, OperationContext ctx, OperationCallback cb);
}
