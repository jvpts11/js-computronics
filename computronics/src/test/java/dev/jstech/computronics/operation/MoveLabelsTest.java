/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoveLabelsTest {

    @Test
    void via_namesTheHostAndTheProgram() {
        assertEquals("lab-pc (Interactor)", MoveLabels.via("lab-pc", MoveLabels.INTERACTOR));
    }

    @Test
    void via_leavesABareHostWhenTheProgramIsBlank() {
        assertEquals("lab-pc", MoveLabels.via("lab-pc", ""));
        assertEquals("lab-pc", MoveLabels.via("lab-pc", null));
    }

    @Test
    void bus_isItsKindWhenUnnamed() {
        assertEquals("Import Bus", MoveLabels.bus("Import Bus", ""));
        assertEquals("Import Bus", MoveLabels.bus("Import Bus", null));
    }

    @Test
    void bus_quotesItsNameWhenNamed() {
        assertEquals("Export Bus \"ores\"", MoveLabels.bus("Export Bus", " ores "));
    }

    @Test
    void hostname_prefersTheConsoleNameLoweredAndDashed() {
        assertEquals("lab-pc", MoveLabels.hostname("Lab PC", "Other Name", "frames_xp"));
    }

    @Test
    void hostname_fallsBackToTheCustomName() {
        assertEquals("east-wing", MoveLabels.hostname("", "East Wing", "frames_xp"));
        assertEquals("east-wing", MoveLabels.hostname(null, "East Wing", "frames_xp"));
    }

    @Test
    void hostname_fallsBackToTheSystemId() {
        assertEquals("frames_xp", MoveLabels.hostname("", "", "frames_xp"));
    }

    @Test
    void hostname_isComputerWhenNothingNamesIt() {
        assertEquals(MoveLabels.DEFAULT_HOSTNAME, MoveLabels.hostname("", " ", null));
    }
}
