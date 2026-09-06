/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.operation;

/**
 * Thrown inside an {@link OperationTask}'s virtual thread when the dispatcher is shut down (the computer powered off, the network broke) while the task was waiting on the main thread or on a tick. The dispatcher catches it; an operation that wants to clean up may also catch it.
 */
public final class OperationCancelledException extends RuntimeException {

    public OperationCancelledException() {
        super("the operation dispatcher was shut down");
    }
}
