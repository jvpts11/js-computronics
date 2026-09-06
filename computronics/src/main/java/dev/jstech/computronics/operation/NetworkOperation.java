/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.core.operation.OperationPriority;

import java.util.UUID;

/**
 * A multi-tick network Operation the Mainframe advances over time — a SELECT, INSERT or DELETE that has been decomposed into SubOperations and streams its items respecting storage latency and the Mainframe's orchestration budget.
 */
public interface NetworkOperation {

    /** The identity views and commands address this Operation by while it is in flight. */
    UUID operationId();

    /** The registered Operation type this is an instance of, such as {@code jsc:select}. */
    String typeId();

    /**
     * True when this Operation must leave no log entry, statistics or lifecycle event of its own when it
     * settles: a placeholder that turned into another Operation, which carries the record instead.
     */
    default boolean silent() {
        return false;
    }

    void tick(long throughputBudget);

    boolean isDone();

    default boolean isWaiting() {
        return false;
    }

    /**
     * Settles the Operation early because the engine can no longer run it (a power-off, a lost network):
     * whatever it held goes back, and it reports how far it got.
     */
    void abandon();

    /**
     * Settles the Operation early because somebody chose to stop it (a task manager, the command prompt):
     * the same clean-up as {@link #abandon()}, but the record reads DISCARDED so the log tells the two apart.
     */
    void cancel();

    /** The level the Mainframe schedules this Operation at; {@link OperationPriority#DEFAULT} unless changed. */
    OperationPriority priority();

    /** Changes the scheduling level; takes effect on the next tick, so a queued Operation can be bumped. */
    void setPriority(OperationPriority priority);

    OperationRecord toRecord();

    OperationRecord liveRecord();
}
