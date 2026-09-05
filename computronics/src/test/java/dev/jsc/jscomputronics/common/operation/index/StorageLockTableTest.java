/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.uuid.NodeUuid;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageLockTableTest {

    private static final String IRON = "iron";
    private static final NodeUuid A = node(1);
    private static final NodeUuid B = node(2);
    private static final UUID OP1 = new UUID(0L, 101L);
    private static final UUID OP2 = new UUID(0L, 102L);

    private StorageLockTable<String> table;

    @BeforeEach
    void setUp() {
        table = new StorageLockTable<>();
    }

    @Test
    void lock_subtractsFromAvailable() {
        table.lock(OP1, IRON, Map.of(A, 50L));

        assertEquals(50L, table.lockedOn(IRON, A));
        assertEquals(50L, table.totalLocked(IRON));
        assertTrue(table.holdsLocks(OP1));
    }

    @Test
    void lock_accumulatesForSameOperation() {
        table.lock(OP1, IRON, Map.of(A, 50L));
        table.lock(OP1, IRON, Map.of(A, 30L, B, 20L));

        assertEquals(80L, table.lockedOn(IRON, A));
        assertEquals(20L, table.lockedOn(IRON, B));
        assertEquals(100L, table.totalLocked(IRON));
    }

    @Test
    void unlock_releasesAllForOperation() {
        table.lock(OP1, IRON, Map.of(A, 50L, B, 20L));

        table.unlock(OP1);

        assertEquals(0L, table.lockedOn(IRON, A));
        assertEquals(0L, table.totalLocked(IRON));
        assertFalse(table.holdsLocks(OP1));
    }

    @Test
    void unlock_onlyReleasesThatOperation() {
        table.lock(OP1, IRON, Map.of(A, 50L));
        table.lock(OP2, IRON, Map.of(A, 30L));

        table.unlock(OP1);

        assertEquals(30L, table.lockedOn(IRON, A), "the other Operation's lock survives");
        assertEquals(30L, table.totalLocked(IRON));
    }

    @Test
    void unlock_unknownOperationIsNoOp() {
        table.lock(OP1, IRON, Map.of(A, 50L));

        table.unlock(new UUID(9L, 9L));

        assertEquals(50L, table.lockedOn(IRON, A));
    }

    @Test
    void lock_ignoresZeroQuantities() {
        table.lock(OP1, IRON, Map.of(A, 0L, B, 10L));

        assertEquals(0L, table.lockedOn(IRON, A));
        assertEquals(10L, table.lockedOn(IRON, B));
    }

    @Test
    void release_dropsThatMuchFromTheReservation() {
        table.lock(OP1, IRON, Map.of(A, 50L));

        table.release(OP1, IRON, A, 20L);

        assertEquals(30L, table.lockedOn(IRON, A), "the moved items leave the lock");
        assertEquals(30L, table.totalLocked(IRON));
        assertTrue(table.holdsLocks(OP1), "the rest is still reserved");
    }

    @Test
    void release_clearsTheReservationWhenFullyReleased() {
        table.lock(OP1, IRON, Map.of(A, 50L));

        table.release(OP1, IRON, A, 50L);

        assertEquals(0L, table.lockedOn(IRON, A));
        assertFalse(table.holdsLocks(OP1));
    }

    @Test
    void release_clampsToWhatTheOperationHolds() {
        table.lock(OP1, IRON, Map.of(A, 50L));

        table.release(OP1, IRON, A, 80L);

        assertEquals(0L, table.lockedOn(IRON, A), "never releases more than was locked");
    }

    @Test
    void release_unknownReservationIsNoOp() {
        table.lock(OP1, IRON, Map.of(A, 50L));

        table.release(OP2, IRON, A, 10L);
        table.release(OP1, IRON, B, 10L);

        assertEquals(50L, table.lockedOn(IRON, A));
    }

    @Test
    void lockedOn_zeroWhenNothingLocked() {
        assertEquals(0L, table.lockedOn(IRON, A));
        assertEquals(0L, table.totalLocked(IRON));
    }

    private static NodeUuid node(final int id) {
        return new NodeUuid(new UUID(0L, id));
    }
}
