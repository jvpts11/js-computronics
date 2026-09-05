/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation;

/**
 * Outcome of running an {@link OperationTask} on a virtual thread.
 */
public sealed interface OperationResult {

    /**
     * The task finished its work successfully.
     */
    record Success() implements OperationResult {
    }

    /**
     * The task failed; {@code reason} is a self-contained, human-readable message.
     */
    record Failure(String reason) implements OperationResult {
    }

    static OperationResult success() {
        return new Success();
    }

    static OperationResult failure(final String reason) {
        return new Failure(reason);
    }
}
