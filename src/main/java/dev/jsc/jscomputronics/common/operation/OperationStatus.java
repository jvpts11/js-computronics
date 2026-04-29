package dev.jsc.jscomputronics.common.operation;

/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 *
 * J's Computronics is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * as published by the Free Software Foundation.
 *
 * J's Computronics is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 */

public enum OperationStatus {
    PENDING,

    PROCESSING,

    IN_PROGRESS,

    COMPLETED,

    FAILED,

    DISCARDED,

    ORPHANED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == DISCARDED;
    }

    public boolean isActive() {
        return this == PENDING || this == PROCESSING || this == IN_PROGRESS;
    }
}
