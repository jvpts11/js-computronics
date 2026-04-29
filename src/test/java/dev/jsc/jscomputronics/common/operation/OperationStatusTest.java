/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 *
 * J's Computronics is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * as published by the Free Software Foundation.
 *
 * J's Computronics is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 */
package dev.jsc.jscomputronics.common.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationStatusTest {

    @Test
    void hasSevenStates() {
        assertEquals(7, OperationStatus.values().length);
    }

    @Test
    void completed_isTerminal() {
        assertTrue(OperationStatus.COMPLETED.isTerminal());
        assertFalse(OperationStatus.COMPLETED.isActive());
    }

    @Test
    void failed_isTerminal() {
        assertTrue(OperationStatus.FAILED.isTerminal());
        assertFalse(OperationStatus.FAILED.isActive());
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
    void inProgress_isActiveNotTerminal() {
        assertTrue(OperationStatus.IN_PROGRESS.isActive());
        assertFalse(OperationStatus.IN_PROGRESS.isTerminal());
    }

    @Test
    void orphaned_isNeitherActiveNorTerminal() {
        assertFalse(OperationStatus.ORPHANED.isActive());
        assertFalse(OperationStatus.ORPHANED.isTerminal());
    }
}
