/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.jstech.computronics.program.ProgramKeybinds.Action;
import org.junit.jupiter.api.Test;

class ProgramKeybindsTest {

    @Test
    void escape_closesTheProgram() {
        assertEquals(Action.CLOSE, ProgramKeybinds.route(ProgramKeybinds.ESCAPE));
    }

    @Test
    void f5_runsTheStatement() {
        assertEquals(Action.RUN, ProgramKeybinds.route(ProgramKeybinds.F5));
    }

    @Test
    void inventoryKey_doesNotClose() {
        // 'E' (GLFW 69) is the default inventory key; routing it to CLOSE was the bug. It must be editing input.
        assertEquals(Action.EDIT, ProgramKeybinds.route(69));
    }

    @Test
    void enter_isEditingNotRunOrClose() {
        // In the multi-line editor, Enter inserts a newline — it must not close or run the program.
        assertEquals(Action.EDIT, ProgramKeybinds.route(ProgramKeybinds.ENTER));
    }

    @Test
    void everyLetterRoutesToEditing() {
        for (int key = 65; key <= 90; key++) { // GLFW A..Z
            assertEquals(Action.EDIT, ProgramKeybinds.route(key), "letter key " + key + " must be editing input");
        }
    }

    @Test
    void onlyEscapeEverCloses() {
        // Exhaustive guard: no key other than Escape may close a program.
        for (int key = 0; key < 400; key++) {
            if (key != ProgramKeybinds.ESCAPE) {
                assertNotEquals(Action.CLOSE, ProgramKeybinds.route(key), "key " + key + " must not close");
            }
        }
    }
}
