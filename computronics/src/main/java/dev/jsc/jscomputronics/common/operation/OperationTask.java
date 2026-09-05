/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation;

/**
 * The CPU-bound work of an Operation, executed on a virtual thread by the {@link OperationDispatch}.
 */
@FunctionalInterface
public interface OperationTask {

    OperationResult run(OperationContext context);
}
