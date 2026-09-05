/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network;

/**
 * Network Category — classifies how a block participates in the network.
 */
public enum NetworkCategory {
    A,
    B,
    C;

    public boolean hasUuid() {
        return this != A;
    }

    public boolean canReceiveOperations() {
        return this == C;
    }
}
