/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation;

/**
 * Outcome of running an {@link IOperationTask} on a virtual thread.
 */
public sealed interface IOperationResult {

    /**
     * The task finished its work successfully.
     */
    record Success() implements IOperationResult {
    }

    /**
     * The task failed; {@code reason} is a self-contained, human-readable message.
     */
    record Failure(String reason) implements IOperationResult {
    }

    static IOperationResult success() {
        return new Success();
    }

    static IOperationResult failure(final String reason) {
        return new Failure(reason);
    }
}
