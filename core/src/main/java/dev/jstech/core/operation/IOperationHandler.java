/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation;

/**
 * Handler contract for operations of a specific {@link OperationType}.
 */
@FunctionalInterface
public interface IOperationHandler<T extends IOperationArgs> {

    OperationStatus execute(T args);
}
