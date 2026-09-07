/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.operation;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelfTestOperationTaskTest {

    /** Runs every marshaled action inline on the calling thread; for testing pure-CPU tasks without a dispatcher. */
    private static final class ImmediateContext implements IOperationContext {
        @Override
        public void onMainThread(final Runnable action) {
            action.run();
        }

        @Override
        public <T> T runOnMain(final Supplier<T> work) {
            return work.get();
        }

        @Override
        public void awaitTicks(final int ticks) {
        }

        @Override
        public long currentTick() {
            return 0L;
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    private static final IOperationContext NO_OP = new ImmediateContext();

    @Test
    void run_succeeds() {
        assertInstanceOf(IOperationResult.Success.class, new SelfTestOperationTask(10_000).run(NO_OP));
    }

    @Test
    void run_zeroWork_stillSucceeds() {
        assertInstanceOf(IOperationResult.Success.class, new SelfTestOperationTask(0).run(NO_OP));
    }

    @Test
    void constructor_rejectsNegativeWork() {
        assertThrows(IllegalArgumentException.class, () -> new SelfTestOperationTask(-1));
    }
}
