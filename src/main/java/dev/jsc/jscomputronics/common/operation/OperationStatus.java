package dev.jsc.jscomputronics.common.operation;

/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */

public enum OperationStatus {

    PENDING,

    PROCESSING,

    WAITING,

    COMPLETED,

    COMPLETED_PARTIAL,

    FAILED,

    RESOURCE_LOCKED,

    DISCARDED;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == COMPLETED_PARTIAL
                || this == FAILED
                || this == RESOURCE_LOCKED
                || this == DISCARDED;
    }

    public boolean isActive() {
        return this == PENDING || this == PROCESSING || this == WAITING;
    }
}
