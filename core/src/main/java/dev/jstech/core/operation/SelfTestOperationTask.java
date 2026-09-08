/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation;

/**
 * A diagnostic Operation that does a bounded amount of deterministic CPU-bound work on its virtual thread and then succeeds.
 */
public record SelfTestOperationTask(int workUnits) implements IOperationTask {

    public SelfTestOperationTask {
        if (workUnits < 0) {
            throw new IllegalArgumentException("workUnits must be >= 0; got " + workUnits);
        }
    }

    @Override
    public IOperationResult run(final IOperationContext context) {
        /*
         * Pure, allocation-free arithmetic over an immutable input; it never touches
         * the world, exactly as an Operation's Layer-A work must behave.
         */
        long accumulator = 0L;
        for (int i = 1; i <= workUnits; i++) {
            accumulator += (long) (Math.sqrt(i) * 1024.0) ^ (long) i;
        }
        // Reference the result so the loop cannot be optimized away.
        return accumulator == Long.MIN_VALUE
                ? IOperationResult.failure("self-test overflow")
                : IOperationResult.success();
    }
}
