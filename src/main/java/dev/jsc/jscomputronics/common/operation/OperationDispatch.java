/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * The Operation dispatcher: runs CPU-bound Operation work on virtual threads while keeping every world mutation on the main (server) thread. A task does its computation on a virtual thread and uses its {@link OperationContext} to bounce world reads/writes back to the main thread and to wait on whole game ticks — so disk latency and throughput are paced deterministically by ticks, never by a wall clock. Multiple disks (one task per server) therefore process in parallel without ever touching the world off-thread.
 */
public final class OperationDispatch implements AutoCloseable, LatencyScheduler {

    private record PendingOp(UUID id, OperationTask task, OperationPriority priority, long sequence) {
    }

    private static final Comparator<PendingOp> ORDER =
            Comparator.comparing(PendingOp::priority).reversed()
                    .thenComparingLong(PendingOp::sequence);

    private static final int MAX_TERMINAL_HISTORY = 256;

    private final int parallelQueues;
    private final ExecutorService workers;
    private final PriorityQueue<PendingOp> pending = new PriorityQueue<>(ORDER);
    private final ConcurrentLinkedQueue<Runnable> mainThreadActions = new ConcurrentLinkedQueue<>();
    private final Map<UUID, OperationStatus> statuses = new ConcurrentHashMap<>();
    // Settle order, so the status map keeps only the most recent terminal entries: without this it would
    // grow one entry per Operation forever on a long-lived dispatcher. Touched on the main thread only.
    private final java.util.ArrayDeque<UUID> terminalOrder = new java.util.ArrayDeque<>();
    private final Set<CompletableFuture<?>> inFlight = ConcurrentHashMap.newKeySet();
    private final Object tickMonitor = new Object();

    private volatile long tickCount;
    private volatile boolean closed;
    private long sequenceCounter;
    private int running;
    private long completed;
    private long failed;

    public OperationDispatch(final int parallelQueues) {
        if (parallelQueues < 1) {
            throw new IllegalArgumentException("parallelQueues must be >= 1; got " + parallelQueues);
        }
        this.parallelQueues = parallelQueues;
        this.workers = Executors.newVirtualThreadPerTaskExecutor();
    }

    public UUID submit(final OperationTask task, final OperationPriority priority) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        final UUID id = UUID.randomUUID();
        statuses.put(id, OperationStatus.PENDING);
        pending.add(new PendingOp(id, task, priority, sequenceCounter++));
        return id;
    }

    /** The current server-tick count; advanced once per {@link #tick()}. */
    public long tickCount() {
        return tickCount;
    }

    public void tick() {
        // 1. Apply everything the virtual threads asked the main thread to do since the last tick.
        Runnable action;
        while ((action = mainThreadActions.poll()) != null) {
            action.run();
        }
        // 2. Promote pending Operations onto virtual threads, up to the parallel-queue capacity.
        while (running < parallelQueues) {
            final PendingOp op = pending.poll();
            if (op == null) {
                break;
            }
            statuses.put(op.id(), OperationStatus.PROCESSING);
            running++;
            dispatch(op);
        }
        // 3. Advance the tick clock and wake any task waiting on ticks (disk latency / pacing).
        synchronized (tickMonitor) {
            tickCount++;
            tickMonitor.notifyAll();
        }
    }

    @Override
    public void afterTicks(final int ticks, final Runnable callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        // Park a virtual thread for the disk's read time, then resume the transfer on the main thread.
        // Many disks call this at once, so their reads genuinely overlap, capped only by the latency.
        workers.execute(() -> {
            try {
                awaitTicks(ticks);
            } catch (final OperationCancelledException cancelled) {
                return; // dispatcher shut down — the Operation is being abandoned, drop the read
            }
            mainThreadActions.add(callback);
        });
    }

    /**
     * Blocks the calling virtual thread until {@code ticks} server ticks have elapsed, or throws
     * {@link OperationCancelledException} if the dispatcher shuts down while waiting. Shared by the
     * per-task context and {@link #afterTicks}.
     */
    private void awaitTicks(final int ticks) {
        if (ticks <= 0) {
            return;
        }
        final long target = tickCount + ticks;
        synchronized (tickMonitor) {
            while (tickCount < target) {
                if (closed) {
                    throw new OperationCancelledException();
                }
                try {
                    tickMonitor.wait();
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new OperationCancelledException();
                }
            }
        }
    }

    private void dispatch(final PendingOp op) {
        final OperationContext context = new DispatchContext();
        workers.execute(() -> {
            OperationResult result;
            try {
                result = op.task().run(context);
                if (result == null) {
                    result = OperationResult.failure("task returned a null result");
                }
            } catch (final OperationCancelledException cancelled) {
                result = OperationResult.failure("cancelled");
            } catch (final Throwable throwable) {
                result = OperationResult.failure(throwable.toString());
            }
            final OperationResult finalResult = result;
            mainThreadActions.add(() -> complete(op.id(), finalResult));
        });
    }

    private void complete(final UUID id, final OperationResult result) {
        if (result instanceof OperationResult.Success) {
            statuses.put(id, OperationStatus.COMPLETED);
            completed++;
        } else {
            statuses.put(id, OperationStatus.FAILED);
            failed++;
        }
        rememberTerminal(id);
        running--;
    }

    public OperationStatus statusOf(final UUID id) {
        return statuses.getOrDefault(id, OperationStatus.PENDING);
    }

    public boolean cancel(final UUID id) {
        if (pending.removeIf(op -> op.id().equals(id))) {
            statuses.put(id, OperationStatus.DISCARDED);
            rememberTerminal(id);
            return true;
        }
        return false;
    }

    /**
     * Records a settled Operation and evicts the oldest once more than {@value #MAX_TERMINAL_HISTORY}
     * have settled, so the status map stays bounded. A caller that polls a just-settled status (the
     * usual pattern) still sees it; only long-stale terminal entries fall back to {@code PENDING}.
     */
    private void rememberTerminal(final UUID id) {
        terminalOrder.addLast(id);
        while (terminalOrder.size() > MAX_TERMINAL_HISTORY) {
            statuses.remove(terminalOrder.pollFirst());
        }
    }

    public int runningCount() {
        return running;
    }

    public int pendingCount() {
        return pending.size();
    }

    public int parallelQueues() {
        return parallelQueues;
    }

    public long completedCount() {
        return completed;
    }

    public long failedCount() {
        return failed;
    }

    @Override
    public void close() {
        closed = true;
        // Unblock every task waiting on the main thread or on a tick so no virtual thread hangs.
        for (final CompletableFuture<?> future : inFlight) {
            future.completeExceptionally(new OperationCancelledException());
        }
        synchronized (tickMonitor) {
            tickMonitor.notifyAll();
        }
        workers.shutdownNow();
    }

    /** The per-task handle: marshals work to the main thread and waits on ticks, all virtual-thread-safe. */
    private final class DispatchContext implements OperationContext {

        @Override
        public void onMainThread(final Runnable mainThreadAction) {
            mainThreadActions.add(mainThreadAction);
        }

        @Override
        public <T> T runOnMain(final Supplier<T> work) {
            if (closed) {
                throw new OperationCancelledException();
            }
            final CompletableFuture<T> future = new CompletableFuture<>();
            inFlight.add(future);
            // Close the race with close(): if a shutdown set closed after our first check but before
            // this add became visible, close()'s drain may have missed this future — re-check now so
            // we never join() on a future nobody will ever complete.
            if (closed) {
                inFlight.remove(future);
                throw new OperationCancelledException();
            }
            mainThreadActions.add(() -> {
                inFlight.remove(future);
                try {
                    future.complete(work.get());
                } catch (final Throwable error) {
                    future.completeExceptionally(error);
                }
            });
            try {
                return future.join();
            } catch (final CompletionException joinError) {
                if (joinError.getCause() instanceof OperationCancelledException cancelled) {
                    throw cancelled;
                }
                throw new OperationCancelledException();
            } finally {
                inFlight.remove(future);
            }
        }

        @Override
        public void awaitTicks(final int ticks) {
            OperationDispatch.this.awaitTicks(ticks);
        }

        @Override
        public long currentTick() {
            return tickCount;
        }

        @Override
        public boolean isActive() {
            return !closed;
        }
    }
}
