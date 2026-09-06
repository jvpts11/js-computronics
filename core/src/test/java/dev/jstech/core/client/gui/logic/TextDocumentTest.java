/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.logic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextDocumentTest {

    private TextDocument doc;

    @BeforeEach
    void setUp() {
        doc = new TextDocument();
    }

    private void type(final String s) {
        for (final char c : s.toCharArray()) {
            doc.insert(c);
        }
    }

    @Test
    void insert_appendsAtTheCaretAndMovesIt() {
        type("abc");
        assertEquals("abc", doc.text());
        assertEquals(3, doc.cursorCol());
        doc.left();
        doc.insert('X');
        assertEquals("abXc", doc.text());
        assertEquals(3, doc.cursorCol());
    }

    @Test
    void newline_splitsTheLineAtTheCaret() {
        type("hello world");
        doc.setCursor(0, 5);
        doc.newline();
        assertEquals(2, doc.lineCount());
        assertEquals("hello", doc.line(0));
        assertEquals(" world", doc.line(1));
        assertEquals(1, doc.cursorLine());
        assertEquals(0, doc.cursorCol());
        assertEquals("hello\n world", doc.text());
    }

    @Test
    void backspace_deletesBeforeTheCaretAndJoinsLines() {
        type("ab");
        doc.newline();
        type("cd");
        doc.backspace();
        assertEquals("c", doc.line(1));
        doc.setCursor(1, 0);
        doc.backspace();
        assertEquals(1, doc.lineCount());
        assertEquals("abc", doc.text());
        assertEquals(2, doc.cursorCol());
        doc.setCursor(0, 0);
        doc.backspace();
        assertEquals("abc", doc.text());
    }

    @Test
    void moves_wrapBetweenLinesAndClampTheColumn() {
        doc.setText("long line\nhi");
        doc.setCursor(0, 9);
        doc.right();
        assertEquals(1, doc.cursorLine());
        assertEquals(0, doc.cursorCol());
        doc.left();
        assertEquals(0, doc.cursorLine());
        assertEquals(9, doc.cursorCol());
        doc.down();
        assertEquals(1, doc.cursorLine());
        assertEquals(2, doc.cursorCol());
        doc.up();
        assertEquals(0, doc.cursorLine());
        assertEquals(2, doc.cursorCol());
        doc.up();
        assertEquals(0, doc.cursorLine());
    }

    @Test
    void setText_roundTripsAndKeepsTrailingEmptyLines() {
        doc.setText("a\n\nb\n");
        assertEquals(4, doc.lineCount());
        assertEquals("a\n\nb\n", doc.text());
        doc.setText("");
        assertEquals(1, doc.lineCount());
        assertEquals("", doc.text());
    }

    @Test
    void setCursor_staysInsideTheText() {
        doc.setText("abc\nde");
        doc.setCursor(9, 9);
        assertEquals(1, doc.cursorLine());
        assertEquals(2, doc.cursorCol());
        doc.setCursor(-1, -1);
        assertEquals(0, doc.cursorLine());
        assertEquals(0, doc.cursorCol());
    }
}
