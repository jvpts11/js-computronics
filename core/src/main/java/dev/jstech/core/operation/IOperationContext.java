/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation;

import java.util.function.Supplier;

/**
 * The handle an {@link IOperationTask} uses while it runs on a virtual thread. The task does its CPU-bound work on the virtual thread, but anything that touches the world, a block entity or the live network index must go back to the main (server) thread, and that is what this context provides. Disk latency and throughput are paced by waiting on whole game ticks, so the whole system stays deterministic (tick-based, never wall-clock).
 */
public interface IOperationContext {

    /** Runs an action on the next server tick and returns immediately (fire-and-forget). */
    void onMainThread(Runnable action);

    /**
     * Runs {@code work} on the next server tick and BLOCKS this virtual thread until it returns the
     * result. Use it to read a snapshot from the main thread, or to apply a world mutation and learn
     * how much actually moved. Cheap on a virtual thread; never call it from the main thread.
     *
     * @throws OperationCancelledException if the dispatcher is shut down before the work runs
     */
    <T> T runOnMain(Supplier<T> work);

    /** Convenience for a main-thread mutation that returns nothing but must complete before continuing. */
    default void doOnMain(final Runnable action) {
        runOnMain(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Blocks this virtual thread until {@code ticks} server ticks have elapsed, the deterministic way
     * to model disk read latency (HDD/SSD/NVMe) and throughput pacing without a wall-clock sleep.
     *
     * @throws OperationCancelledException if the dispatcher is shut down while waiting
     */
    void awaitTicks(int ticks);

    /** The dispatcher's running server-tick count, for an operation that paces itself by absolute ticks. */
    long currentTick();

    /** Whether the dispatcher is still running; a long operation should bail out when this turns false. */
    boolean isActive();
}
