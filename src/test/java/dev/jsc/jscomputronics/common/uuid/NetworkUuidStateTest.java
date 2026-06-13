/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.uuid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkUuidStateTest {

    @Test
    void values_hasThreeStates() {
        assertEquals(3, NetworkUuidState.values().length);
    }

    @Test
    void active_isOperational() {
        assertTrue(NetworkUuidState.ACTIVE.isOperational());
    }

    @Test
    void orphaned_isNotOperational() {
        assertFalse(NetworkUuidState.ORPHANED.isOperational());
    }

    @Test
    void conflicted_isNotOperational() {
        assertFalse(NetworkUuidState.CONFLICTED.isOperational());
    }

    @Test
    void active_isNotRecoverable() {
        // ACTIVE is the goal state; "recoverable" applies to abnormal states.
        assertFalse(NetworkUuidState.ACTIVE.isRecoverable());
    }

    @Test
    void orphaned_isRecoverable() {
        assertTrue(NetworkUuidState.ORPHANED.isRecoverable());
    }

    @Test
    void conflicted_isRecoverable() {
        assertTrue(NetworkUuidState.CONFLICTED.isRecoverable());
    }
}
