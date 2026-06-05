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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Operation dispatcher: runs CPU-bound Operation work on virtual threads while keeping every world mutation on the main (server) thread.
 */
public final class OperationDispatch implements AutoCloseable {

    private record PendingOp(UUID id, OperationTask task, OperationPriority priority, long sequence) {
    }

    private static final Comparator<PendingOp> ORDER =
            Comparator.comparing(PendingOp::priority).reversed()
                    .thenComparingLong(PendingOp::sequence);

    private final int parallelQueues;
    private final ExecutorService workers;
    private final PriorityQueue<PendingOp> pending = new PriorityQueue<>(ORDER);
    private final ConcurrentLinkedQueue<Runnable> mainThreadActions = new ConcurrentLinkedQueue<>();
    private final Map<UUID, OperationStatus> statuses = new ConcurrentHashMap<>();

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

    public void tick() {
        Runnable action;
        while ((action = mainThreadActions.poll()) != null) {
            action.run();
        }
        while (running < parallelQueues) {
            final PendingOp op = pending.poll();
            if (op == null) {
                break;
            }
            statuses.put(op.id(), OperationStatus.PROCESSING);
            running++;
            dispatch(op);
        }
    }

    private void dispatch(final PendingOp op) {
        final OperationContext context = mainThreadActions::add;
        workers.execute(() -> {
            OperationResult result;
            try {
                result = op.task().run(context);
                if (result == null) {
                    result = OperationResult.failure("task returned a null result");
                }
            } catch (final Throwable throwable) {
                result = OperationResult.failure(throwable.toString());
            }
            final OperationResult finalResult = result;
            mainThreadActions.add(() -> complete(op.id(), finalResult));
        });
    }

    private void complete(final UUID id, final OperationResult result) {
        // Only Success and Failure exist today; revisit when InProgress (SubOperations) is added.
        if (result instanceof OperationResult.Success) {
            statuses.put(id, OperationStatus.COMPLETED);
            completed++;
        } else {
            statuses.put(id, OperationStatus.FAILED);
            failed++;
        }
        running--;
    }

    public OperationStatus statusOf(final UUID id) {
        return statuses.getOrDefault(id, OperationStatus.PENDING);
    }

    public boolean cancel(final UUID id) {
        if (pending.removeIf(op -> op.id().equals(id))) {
            statuses.put(id, OperationStatus.DISCARDED);
            return true;
        }
        return false;
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
        workers.shutdown();
    }
}
