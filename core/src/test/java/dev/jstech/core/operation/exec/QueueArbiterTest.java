/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.operation.exec;

import dev.jstech.core.operation.OperationPriority;
import dev.jstech.core.operation.exec.QueueArbiter.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueArbiterTest {

    private static final int AGING = 600;

    private static Candidate<String> ready(final String name, final OperationPriority priority) {
        return new Candidate<>(name, priority, 0);
    }

    private static Candidate<String> deferred(final String name, final OperationPriority priority,
                                              final int ticks) {
        return new Candidate<>(name, priority, ticks);
    }

    @Test
    void grant_prefersHigherPriorityOverSubmissionOrder() {
        final List<Candidate<String>> ready = List.of(
                ready("first-medium", OperationPriority.MEDIUM),
                ready("second-medium", OperationPriority.MEDIUM),
                ready("third-high", OperationPriority.HIGH));
        assertEquals(List.of("third-high"), QueueArbiter.grant(ready, 1, AGING));
    }

    @Test
    void grant_keepsSubmissionOrderAmongEquals() {
        final List<Candidate<String>> ready = List.of(
                ready("a", OperationPriority.MEDIUM),
                ready("b", OperationPriority.MEDIUM),
                ready("c", OperationPriority.MEDIUM));
        assertEquals(List.of("a", "b"), QueueArbiter.grant(ready, 2, AGING));
    }

    @Test
    void grant_returnsWinnersInGivenOrder() {
        final List<Candidate<String>> ready = List.of(
                ready("low", OperationPriority.LOW),
                ready("high", OperationPriority.HIGH),
                ready("medium", OperationPriority.MEDIUM));
        // Two slots: high and medium win; the list keeps their original relative order (high before medium).
        assertEquals(List.of("high", "medium"), QueueArbiter.grant(ready, 2, AGING));
    }

    @Test
    void grant_moreSlotsThanCandidates_grantsAll() {
        final List<Candidate<String>> ready = List.of(
                ready("a", OperationPriority.LOW),
                ready("b", OperationPriority.HIGH));
        assertEquals(List.of("a", "b"), QueueArbiter.grant(ready, 5, AGING));
    }

    @Test
    void grant_zeroSlots_grantsNothing() {
        assertTrue(QueueArbiter.grant(List.of(ready("a", OperationPriority.HIGH)), 0, AGING).isEmpty());
    }

    @Test
    void grant_emptyList_grantsNothing() {
        assertTrue(QueueArbiter.grant(List.<Candidate<String>>of(), 3, AGING).isEmpty());
    }

    @Test
    void effective_liftsOneLevelPerAgingPeriod() {
        assertEquals(OperationPriority.LOW, QueueArbiter.effective(OperationPriority.LOW, 599, AGING));
        assertEquals(OperationPriority.MEDIUM_LOW, QueueArbiter.effective(OperationPriority.LOW, 600, AGING));
        assertEquals(OperationPriority.MEDIUM, QueueArbiter.effective(OperationPriority.LOW, 1200, AGING));
    }

    @Test
    void effective_capsAtHigh() {
        assertEquals(OperationPriority.HIGH, QueueArbiter.effective(OperationPriority.LOW, 600 * 40, AGING));
        assertEquals(OperationPriority.HIGH, QueueArbiter.effective(OperationPriority.HIGH, 600, AGING));
    }

    @Test
    void effective_agingDisabled_keepsPriority() {
        assertEquals(OperationPriority.LOW, QueueArbiter.effective(OperationPriority.LOW, 100_000, 0));
        assertEquals(OperationPriority.LOW, QueueArbiter.effective(OperationPriority.LOW, 100_000, -5));
    }

    @Test
    void grant_agedLowOpOutranksFreshMediumOp() {
        final List<Candidate<String>> ready = List.of(
                deferred("starved-low", OperationPriority.LOW, 1300), // two levels up: MEDIUM
                ready("fresh-medium", OperationPriority.MEDIUM));
        // Equal effective level: submission order decides, and the starved one came first.
        assertEquals(List.of("starved-low"), QueueArbiter.grant(ready, 1, AGING));
    }

    @Test
    void grant_agedLowOpOutranksFreshHighOnceCapped() {
        final List<Candidate<String>> ready = List.of(
                ready("fresh-high", OperationPriority.HIGH),
                deferred("starved-low", OperationPriority.LOW, 600 * 4));
        // Both compete at HIGH now; the fresh one was listed first, so it keeps the slot.
        assertEquals(List.of("fresh-high"), QueueArbiter.grant(ready, 1, AGING));
        final List<Candidate<String>> reversed = List.of(
                deferred("starved-low", OperationPriority.LOW, 600 * 4),
                ready("fresh-high", OperationPriority.HIGH));
        assertEquals(List.of("starved-low"), QueueArbiter.grant(reversed, 1, AGING));
    }
}
