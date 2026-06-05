/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class OperationDispatchTest {

    private OperationDispatch dispatch;

    @AfterEach
    void tearDown() {
        if (dispatch != null) {
            dispatch.close();
        }
    }

    private void tickUntilTerminal(final UUID id) {
        for (int attempt = 0; attempt < 200; attempt++) {
            dispatch.tick();
            if (dispatch.statusOf(id).isTerminal()) {
                return;
            }
            sleep(5);
        }
        fail("operation " + id + " did not reach a terminal state");
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void successfulTask_completes() {
        dispatch = new OperationDispatch(1);
        final UUID id = dispatch.submit(context -> OperationResult.success(), OperationPriority.MEDIUM);
        tickUntilTerminal(id);
        assertEquals(OperationStatus.COMPLETED, dispatch.statusOf(id));
    }

    @Test
    void throwingTask_marksFailedWithoutCrashing() {
        dispatch = new OperationDispatch(1);
        final UUID id = dispatch.submit(context -> {
            throw new IllegalStateException("boom");
        }, OperationPriority.MEDIUM);
        tickUntilTerminal(id);
        assertEquals(OperationStatus.FAILED, dispatch.statusOf(id));
    }

    @Test
    void nullResult_marksFailed() {
        dispatch = new OperationDispatch(1);
        final UUID id = dispatch.submit(context -> null, OperationPriority.MEDIUM);
        tickUntilTerminal(id);
        assertEquals(OperationStatus.FAILED, dispatch.statusOf(id));
    }

    @Test
    void unknownId_reportsPending() {
        dispatch = new OperationDispatch(1);
        assertEquals(OperationStatus.PENDING, dispatch.statusOf(UUID.randomUUID()));
    }

    @Test
    void sideEffect_runsOnTickThreadAndTaskOnVirtualThread() {
        dispatch = new OperationDispatch(1);
        final Thread tickThread = Thread.currentThread();
        final AtomicReference<Thread> taskThread = new AtomicReference<>();
        final AtomicReference<Thread> sideEffectThread = new AtomicReference<>();

        final UUID id = dispatch.submit(context -> {
            taskThread.set(Thread.currentThread());
            context.onMainThread(() -> sideEffectThread.set(Thread.currentThread()));
            return OperationResult.success();
        }, OperationPriority.MEDIUM);

        tickUntilTerminal(id);

        assertEquals(tickThread, sideEffectThread.get(),
                "world side effects must run on the tick (main) thread");
        assertNotEquals(tickThread, taskThread.get(),
                "task work must run off the main thread (a virtual thread)");
        assertTrue(taskThread.get().isVirtual(), "task must run on a virtual thread");
    }

    @Test
    void higherPriority_runsFirst() {
        dispatch = new OperationDispatch(1); // single queue forces a strict order
        final List<String> order = new CopyOnWriteArrayList<>();
        final UUID low = dispatch.submit(context -> {
            order.add("low");
            return OperationResult.success();
        }, OperationPriority.LOW);
        final UUID high = dispatch.submit(context -> {
            order.add("high");
            return OperationResult.success();
        }, OperationPriority.HIGH);

        tickUntilTerminal(low);
        tickUntilTerminal(high);

        assertEquals(List.of("high", "low"), order);
    }

    @Test
    void concurrency_neverExceedsQueueCount() {
        dispatch = new OperationDispatch(2); // two parallel queues
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();

        final OperationTask blocking = context -> {
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            try {
                release.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            concurrent.decrementAndGet();
            return OperationResult.success();
        };

        final UUID a = dispatch.submit(blocking, OperationPriority.MEDIUM);
        final UUID b = dispatch.submit(blocking, OperationPriority.MEDIUM);
        final UUID c = dispatch.submit(blocking, OperationPriority.MEDIUM);

        dispatch.tick(); // promote up to two
        for (int i = 0; i < 200 && concurrent.get() < 2; i++) {
            sleep(5);
        }

        assertEquals(2, dispatch.runningCount());
        assertEquals(1, dispatch.pendingCount());

        release.countDown();
        tickUntilTerminal(a);
        tickUntilTerminal(b);
        tickUntilTerminal(c);

        assertEquals(2, maxConcurrent.get(), "no more than two tasks may run at once");
        assertEquals(OperationStatus.COMPLETED, dispatch.statusOf(c));
    }

    @Test
    void cancelPending_discards() {
        dispatch = new OperationDispatch(1);
        final UUID id = dispatch.submit(context -> OperationResult.success(), OperationPriority.MEDIUM);
        assertTrue(dispatch.cancel(id));
        assertEquals(OperationStatus.DISCARDED, dispatch.statusOf(id));
        assertEquals(0, dispatch.pendingCount());
    }

    @Test
    void cancelUnknown_returnsFalse() {
        dispatch = new OperationDispatch(1);
        assertFalse(dispatch.cancel(UUID.randomUUID()));
    }

    @Test
    void constructor_rejectsZeroQueues() {
        try {
            new OperationDispatch(0);
            fail("expected IllegalArgumentException");
        } catch (final IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    void completedAndFailedCounts_tallyTerminalOutcomes() {
        dispatch = new OperationDispatch(1);
        final UUID ok = dispatch.submit(context -> OperationResult.success(), OperationPriority.MEDIUM);
        tickUntilTerminal(ok);
        final UUID bad = dispatch.submit(context -> OperationResult.failure("nope"), OperationPriority.MEDIUM);
        tickUntilTerminal(bad);
        assertEquals(1L, dispatch.completedCount());
        assertEquals(1L, dispatch.failedCount());
    }

    @Test
    void parallelQueues_reportsConfiguredCount() {
        dispatch = new OperationDispatch(3);
        assertEquals(3, dispatch.parallelQueues());
    }
}
