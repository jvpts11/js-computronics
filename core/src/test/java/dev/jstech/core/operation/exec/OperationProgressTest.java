/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.operation.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationProgressTest {

    @Test
    void aggregates_movedAndCompletionAcrossSources() {
        final TransferState a = new TransferState(30, 0);
        final TransferState b = new TransferState(20, 0);
        final OperationProgress progress = new OperationProgress(List.of(a, b));

        for (int i = 0; i < 3; i++) {
            a.tick(10);
            b.tick(10);
        }

        assertEquals(50L, progress.movedTotal());
        assertEquals(50L, progress.total());
        assertTrue(progress.isComplete());
    }

    @Test
    void isComplete_onlyWhenEverySourceIsDone() {
        final TransferState fast = new TransferState(10, 0);
        final TransferState slow = new TransferState(10, 5);
        final OperationProgress progress = new OperationProgress(List.of(fast, slow));

        fast.tick(10);
        slow.tick(10);
        assertFalse(progress.isComplete(), "one source is still waiting out its latency");

        for (int i = 0; i < 6; i++) {
            slow.tick(10);
        }
        assertTrue(progress.isComplete());
    }

    @Test
    void percent_reflectsOverallProgress() {
        final TransferState a = new TransferState(50, 0);
        final TransferState b = new TransferState(50, 0);
        final OperationProgress progress = new OperationProgress(List.of(a, b));

        assertEquals(0, progress.percent());
        a.tick(50);
        assertEquals(50, progress.percent(), "half the total moved");
        b.tick(50);
        assertEquals(100, progress.percent());
    }

    @Test
    void emptyOperation_isCompleteAtFullPercent() {
        final OperationProgress progress = new OperationProgress(List.of());

        assertTrue(progress.isComplete());
        assertEquals(100, progress.percent());
        assertEquals(0, progress.sourceCount());
    }
}
