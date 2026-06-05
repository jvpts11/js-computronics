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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // NetworkUuidStatus tests below — kept in the same file because they
    // are tightly coupled to the state semantics.

    @Test
    void status_activeFactory_setsActive() {
        var status = NetworkUuidStatus.active(NetworkUuid.random());
        assertSame(NetworkUuidState.ACTIVE, status.state());
    }

    @Test
    void status_orphanedFactory_setsOrphaned() {
        var status = NetworkUuidStatus.orphaned(NetworkUuid.random());
        assertSame(NetworkUuidState.ORPHANED, status.state());
    }

    @Test
    void status_conflictedFactory_setsConflicted() {
        var status = NetworkUuidStatus.conflicted(NetworkUuid.random());
        assertSame(NetworkUuidState.CONFLICTED, status.state());
    }

    @Test
    void status_withState_preservesUuid() {
        var uuid = NetworkUuid.random();
        var original = NetworkUuidStatus.active(uuid);
        var transitioned = original.withState(NetworkUuidState.ORPHANED);
        assertEquals(uuid, transitioned.uuid());
        assertSame(NetworkUuidState.ORPHANED, transitioned.state());
    }

    @Test
    void status_rejectsNullUuid() {
        assertThrows(NullPointerException.class,
                () -> new NetworkUuidStatus(null, NetworkUuidState.ACTIVE));
    }

    @Test
    void status_rejectsNullState() {
        assertThrows(NullPointerException.class,
                () -> new NetworkUuidStatus(NetworkUuid.random(), null));
    }
}
