/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.operation.payload.OperationRecord;

/**
 * A multi-tick network Operation the Mainframe advances over time — a SELECT, INSERT or DELETE that has been decomposed into SubOperations and streams its items respecting storage latency and the Mainframe's orchestration budget.
 */
public interface NetworkOperation {

    void tick(long throughputBudget);

    boolean isDone();

    default boolean isWaiting() {
        return false;
    }

    void abandon();

    OperationRecord toRecord();

    OperationRecord liveRecord();
}
