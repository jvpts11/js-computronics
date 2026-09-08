/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransferStateTest {

    @Test
    void tick_waitsOutLatencyThenStreamsAtThroughput() {
        final TransferState transfer = new TransferState(25, 3);

        assertTrue(transfer.waitingOnLatency());
        for (int i = 0; i < 3; i++) {
            assertEquals(0L, transfer.tick(10), "no item moves during read latency");
        }
        assertEquals(10L, transfer.tick(10), "first streaming tick moves a full throughput");
        assertEquals(10L, transfer.tick(10));
        assertEquals(5L, transfer.tick(10), "the last tick moves only the remainder");
        assertTrue(transfer.isComplete());
        assertEquals(0L, transfer.tick(10), "a complete transfer moves nothing further");
    }

    @Test
    void tick_capsEachTickAtTheGivenThroughput() {
        final TransferState transfer = new TransferState(100, 0);

        assertEquals(16L, transfer.tick(16));
        assertEquals(16L, transfer.tick(16));
        assertEquals(32L, transfer.moved());
    }

    @Test
    void tick_zeroThroughputMakesNoProgress() {
        final TransferState transfer = new TransferState(10, 0);

        assertEquals(0L, transfer.tick(0), "a starved source moves nothing");
        assertEquals(0L, transfer.moved());
        assertFalse(transfer.isComplete());
        assertEquals(10L, transfer.tick(10), "and resumes once throughput returns");
    }

    @Test
    void tick_completesExactlyAtTotal() {
        final TransferState transfer = new TransferState(7, 0);

        assertEquals(3L, transfer.tick(3));
        assertEquals(3L, transfer.tick(3));
        assertEquals(1L, transfer.tick(3));
        assertTrue(transfer.isComplete());
        assertEquals(7L, transfer.moved());
        assertEquals(0L, transfer.remaining());
    }

    @Test
    void isComplete_returnsTrueWhenTotalIsZero() {
        final TransferState transfer = new TransferState(0, 5);

        assertTrue(transfer.isComplete());
        assertEquals(0L, transfer.tick(10));
    }

    @Test
    void waitingOnLatency_clearsOnceItemsStream() {
        final TransferState transfer = new TransferState(10, 1);

        assertTrue(transfer.waitingOnLatency());
        transfer.tick(10);
        assertFalse(transfer.waitingOnLatency());
    }

    @Test
    void constructor_rejectsNegativeTotal() {
        assertThrows(IllegalArgumentException.class, () -> new TransferState(-1, 0));
    }
}
