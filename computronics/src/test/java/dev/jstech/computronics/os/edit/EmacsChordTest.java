/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmacsChordTest {

    @Test
    void of_readsSaveAndQuitApart() {
        assertEquals(EmacsChord.Action.SAVE, EmacsChord.of("C-x C-s"));
        assertEquals(EmacsChord.Action.QUIT, EmacsChord.of("C-x C-c"));
    }

    @Test
    void of_waitsWhileARunCouldStillBecomeSomething() {
        assertEquals(EmacsChord.Action.PENDING, EmacsChord.of("C-x"));
        assertEquals(EmacsChord.Action.PENDING, EmacsChord.of("M-x"));
        assertEquals(EmacsChord.Action.PENDING, EmacsChord.of(""));
    }

    @Test
    void of_readsTheCancel() {
        assertEquals(EmacsChord.Action.CANCEL, EmacsChord.of("C-g"));
    }

    @Test
    void of_readsCompile() {
        assertEquals(EmacsChord.Action.COMPILE, EmacsChord.of("M-x compile"));
    }

    @Test
    void of_refusesARunNobodyKnows() {
        assertEquals(EmacsChord.Action.UNKNOWN, EmacsChord.of("C-x C-z"));
        assertEquals(EmacsChord.Action.UNKNOWN, EmacsChord.of("C-q"));
        assertEquals(EmacsChord.Action.UNKNOWN, EmacsChord.of("M-x nothing"));
    }

    @Test
    void of_ignoresTheSpacesAroundIt() {
        assertEquals(EmacsChord.Action.SAVE, EmacsChord.of("  C-x C-s  "));
    }

    @Test
    void of_treatsNullAsNothingTypedYet() {
        assertEquals(EmacsChord.Action.PENDING, EmacsChord.of(null));
    }

    @Test
    void couldGrow_isTrueForTheStartOfACommand() {
        assertTrue(EmacsChord.couldGrow("C-x"));
        assertTrue(EmacsChord.couldGrow("M-x"));
        assertTrue(EmacsChord.couldGrow("M-x comp"));
        assertTrue(EmacsChord.couldGrow(""));
        assertTrue(EmacsChord.couldGrow(null));
    }

    @Test
    void couldGrow_isFalseOnceARunCannotBecomeAnything() {
        assertFalse(EmacsChord.couldGrow("C-x C-z"));
        assertFalse(EmacsChord.couldGrow("C-q"));
    }

    @Test
    void couldGrow_isTrueForACommandThatIsAlreadyWhole() {
        // A finished command is still a run of itself, which is what stops it being thrown away.
        assertTrue(EmacsChord.couldGrow("C-x C-s"));
    }

    @Test
    void key_writesAHeldKeyTheWayEmacsDoes() {
        assertEquals("C-x", EmacsChord.key("x", true, false));
        assertEquals("M-x", EmacsChord.key("x", false, true));
        assertEquals("x", EmacsChord.key("x", false, false));
    }

    @Test
    void key_prefersControlWhenBothAreHeld() {
        assertEquals("C-x", EmacsChord.key("x", true, true));
    }

    @Test
    void unknown_saysWhatWasPressed() {
        assertEquals("C-x C-z is undefined", EmacsChord.unknown("C-x C-z"));
    }
}
