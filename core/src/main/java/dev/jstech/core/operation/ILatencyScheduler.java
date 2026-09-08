/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation;

/**
 * Schedules a callback to run after a number of server ticks elapse, parking a virtual thread in the meantime. A timed Operation uses it to model a disk's read latency: each disk parks its own virtual thread for its read time, so several disks wait in parallel without ever touching the world off-thread, and the callback that resumes the transfer runs back on the main (server) thread.
 */
public interface ILatencyScheduler {

    /**
     * Runs {@code callback} on the main thread on the first server tick after {@code ticks} ticks have
     * elapsed. If {@code ticks <= 0} the callback runs on the next tick. If the dispatcher shuts down
     * while the virtual thread is parked, the callback is dropped (the Operation is abandoned anyway).
     */
    void afterTicks(int ticks, Runnable callback);
}
