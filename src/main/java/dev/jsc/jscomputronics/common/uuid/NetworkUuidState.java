/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.uuid;

public enum NetworkUuidState {

    ACTIVE,

    ORPHANED,

    CONFLICTED;

    public boolean isOperational() {
        return this == ACTIVE;
    }

    public boolean isRecoverable() {
        return this == ORPHANED || this == CONFLICTED;
    }
}
