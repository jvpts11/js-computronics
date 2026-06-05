/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelfTestOperationTaskTest {

    private static final OperationContext NO_OP = action -> {
    };

    @Test
    void run_succeeds() {
        assertInstanceOf(OperationResult.Success.class, new SelfTestOperationTask(10_000).run(NO_OP));
    }

    @Test
    void run_zeroWork_stillSucceeds() {
        assertInstanceOf(OperationResult.Success.class, new SelfTestOperationTask(0).run(NO_OP));
    }

    @Test
    void constructor_rejectsNegativeWork() {
        assertThrows(IllegalArgumentException.class, () -> new SelfTestOperationTask(-1));
    }
}
