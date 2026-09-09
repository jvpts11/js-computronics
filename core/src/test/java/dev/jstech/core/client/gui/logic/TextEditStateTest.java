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

    @Test
    public void caret_startsAfterTheTextAndMovesWithinIt() {
        final TextEditState state = new TextEditState(16);
        state.sync("file.can");
        assertEquals(8, state.caret());
        state.left();
        state.left();
        assertEquals(6, state.caret());
        state.home();
        assertEquals(0, state.caret());
        state.left();
        assertEquals(0, state.caret(), "the caret never leaves the text on the left");
        state.end();
        state.right();
        assertEquals(8, state.caret(), "nor on the right");
        state.setCaret(4);
        assertEquals(4, state.caret());
        state.setCaret(99);
        assertEquals(8, state.caret(), "a place past the end is the end");
    }

    @Test
    public void type_insertsAtTheCaretAndBackspaceDeleteWorkAroundIt() {
        final TextEditState state = new TextEditState(16);
        state.sync("file.can");
        state.setCaret(4);
        state.type('s');
        assertEquals("files.can", state.edit());
        assertEquals(5, state.caret());
        state.backspace();
        assertEquals("file.can", state.edit());
        assertEquals(4, state.caret());
        state.delete();
        assertEquals("filecan", state.edit());
        assertEquals(4, state.caret(), "delete takes what is after the caret and leaves it where it was");
        state.home();
        state.backspace();
        assertEquals("filecan", state.edit(), "nothing before the caret, nothing removed");
        state.end();
        state.delete();
        assertEquals("filecan", state.edit(), "nothing after the caret, nothing removed");
    }

    @Test
    public void revert_putsTheCaretBackAfterTheValue() {
        final TextEditState state = new TextEditState(16);
        state.sync("abc");
        state.home();
        state.type('x');
        state.revert();
        assertEquals("abc", state.edit());
        assertEquals(3, state.caret());
    }
}
