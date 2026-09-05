/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.operation;

/**
 * A diagnostic Operation that does a bounded amount of deterministic CPU-bound work on its virtual thread and then succeeds.
 */
public record SelfTestOperationTask(int workUnits) implements OperationTask {

    public SelfTestOperationTask {
        if (workUnits < 0) {
            throw new IllegalArgumentException("workUnits must be >= 0; got " + workUnits);
        }
    }

    @Override
    public OperationResult run(final OperationContext context) {
        // Pure, allocation-free arithmetic over an immutable input — never touches
        // the world, exactly as an Operation's Layer-A work must behave.
        long accumulator = 0L;
        for (int i = 1; i <= workUnits; i++) {
            accumulator += (long) (Math.sqrt(i) * 1024.0) ^ (long) i;
        }
        // Reference the result so the loop cannot be optimized away.
        return accumulator == Long.MIN_VALUE
                ? OperationResult.failure("self-test overflow")
                : OperationResult.success();
    }
}
