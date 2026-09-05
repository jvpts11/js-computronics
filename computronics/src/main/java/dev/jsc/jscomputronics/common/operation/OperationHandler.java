/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation;

/**
 * Handler contract for operations of a specific {@link OperationType}.
 */
@FunctionalInterface
public interface OperationHandler<T extends OperationArgs> {

    OperationStatus execute(T args);
}
