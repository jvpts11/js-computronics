/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation;

/**
 * The context handed to an {@link OperationTask} while it runs on a virtual thread.
 */
public interface OperationContext {

    void onMainThread(Runnable action);
}
