/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextEditStateTest {

    @Test
    public void type_appendsUntilTheMaximumLength() {
        final TextEditState state = new TextEditState(3);
        state.type('a');
        state.type('b');
        state.type('c');
        state.type('d');
        assertEquals("abc", state.edit());
        assertEquals("", state.value());
        assertTrue(state.dirty());
    }

    @Test
    public void backspace_removesTheLastCharacterAndDoesNothingOnEmptyText() {
        final TextEditState state = new TextEditState(8);
        state.backspace();
        assertEquals("", state.edit());
        state.type('x');
        state.type('y');
        state.backspace();
        assertEquals("x", state.edit());
    }

    @Test
    public void commit_makesTheEditTheValue() {
        final TextEditState state = new TextEditState(8);
        state.type('o');
        state.type('k');
        state.commit();
        assertEquals("ok", state.value());
        assertFalse(state.dirty());
    }

    @Test
    public void revert_dropsTheEditsAndKeepsTheValue() {
        final TextEditState state = new TextEditState(8);
        state.sync("kept");
        state.type('!');
        assertTrue(state.dirty());
        state.revert();
        assertEquals("kept", state.edit());
        assertFalse(state.dirty());
    }

    @Test
    public void sync_adoptsTheValueAndTreatsNullAsEmpty() {
        final TextEditState state = new TextEditState(8);
        state.sync("new");
        assertEquals("new", state.value());
        assertEquals("new", state.edit());
        state.sync(null);
        assertEquals("", state.value());
    }
}
