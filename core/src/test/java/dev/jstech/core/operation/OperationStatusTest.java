/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationStatusTest {

    @Test
    void values_hasEightStates() {
        assertEquals(8, OperationStatus.values().length);
    }

    @Test
    void completed_isTerminal() {
        assertTrue(OperationStatus.COMPLETED.isTerminal());
        assertFalse(OperationStatus.COMPLETED.isActive());
    }

    @Test
    void completedPartial_isTerminal() {
        assertTrue(OperationStatus.COMPLETED_PARTIAL.isTerminal());
        assertFalse(OperationStatus.COMPLETED_PARTIAL.isActive());
    }

    @Test
    void failed_isTerminal() {
        assertTrue(OperationStatus.FAILED.isTerminal());
        assertFalse(OperationStatus.FAILED.isActive());
    }

    @Test
    void resourceLocked_isTerminal() {
        assertTrue(OperationStatus.RESOURCE_LOCKED.isTerminal());
        assertFalse(OperationStatus.RESOURCE_LOCKED.isActive());
    }

    @Test
    void discarded_isTerminal() {
        assertTrue(OperationStatus.DISCARDED.isTerminal());
        assertFalse(OperationStatus.DISCARDED.isActive());
    }

    @Test
    void pending_isActiveNotTerminal() {
        assertTrue(OperationStatus.PENDING.isActive());
        assertFalse(OperationStatus.PENDING.isTerminal());
    }

    @Test
    void processing_isActiveNotTerminal() {
        assertTrue(OperationStatus.PROCESSING.isActive());
        assertFalse(OperationStatus.PROCESSING.isTerminal());
    }

    @Test
    void waiting_isActiveNotTerminal() {
        assertTrue(OperationStatus.WAITING.isActive());
        assertFalse(OperationStatus.WAITING.isTerminal());
    }

    @Test
    void everyState_isExactlyOneOfTerminalOrActive() {
        for (var state : OperationStatus.values()) {
            // Must be one or the other, never both, never neither.
            boolean terminal = state.isTerminal();
            boolean active = state.isActive();
            assertTrue(
                    terminal ^ active,
                    "State " + state + " must be exactly one of {terminal, active}; "
                            + "got terminal=" + terminal + ", active=" + active
            );
        }
    }
}
