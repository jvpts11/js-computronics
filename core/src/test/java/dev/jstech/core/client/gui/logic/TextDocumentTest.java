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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void insertText_typesEveryCharacterAndSplitsOnNewlines() {
        doc.setText("ab");
        doc.setCursor(0, 1);
        doc.insertText("X\nY");
        assertEquals("aX\nYb", doc.text());
        assertEquals(1, doc.cursorLine());
        assertEquals(1, doc.cursorCol());
    }

    @Test
    void find_movesToTheNextMatchAfterTheCaretAndGoesRound() {
        doc.setText("one two\nthree two\ntwo");
        assertTrue(doc.find("two"));
        assertEquals(0, doc.cursorLine());
        assertEquals(4, doc.cursorCol());
        assertTrue(doc.find("two"));
        assertEquals(1, doc.cursorLine());
        assertEquals(6, doc.cursorCol());
        assertTrue(doc.find("two"));
        assertEquals(2, doc.cursorLine());
        assertTrue(doc.find("two"), "the search goes round to the top");
        assertEquals(0, doc.cursorLine());
        assertEquals(4, doc.cursorCol());
    }

    @Test
    void find_saysNoForNothingAndForWhatIsNotThere() {
        doc.setText("abc");
        assertFalse(doc.find(""));
        assertFalse(doc.find(null));
        assertFalse(doc.find("zzz"));
        assertEquals(0, doc.cursorCol(), "a miss leaves the caret alone");
    }

    @Test
    void find_findsTheOnlyMatchWhenItSitsUnderTheCaret() {
        doc.setText("  needle");
        doc.setCursor(0, 2);
        assertTrue(doc.find("needle"), "the one match, at the caret, is still found by going round");
        assertEquals(2, doc.cursorCol());
    }

    @Test
    void toggleLinePrefix_commentsALineOutAndBackIn() {
        doc.setText("    x = 1;");
        doc.setCursor(0, 8);
        doc.toggleLinePrefix("// ");
        assertEquals("    // x = 1;", doc.text());
        assertEquals(11, doc.cursorCol(), "the caret keeps its place in the text");
        doc.toggleLinePrefix("// ");
        assertEquals("    x = 1;", doc.text());
        assertEquals(8, doc.cursorCol());
    }

    @Test
    void selection_growsWithExtendedMovesAndReadsBackInOrder() {
        doc.setText("one\ntwo\nthree");
        doc.setCursor(0, 1);
        doc.right(true);
        doc.down(true);
        assertTrue(doc.hasSelection());
        assertEquals("ne\ntw", doc.selectedText());
        assertEquals(new TextDocument.Spot(0, 1), doc.selectionStart());
        assertEquals(new TextDocument.Spot(1, 2), doc.selectionEnd());
        // Selecting backwards reads the same stretch forwards.
        doc.setCursor(1, 2);
        doc.setCursor(0, 1, true);
        assertEquals("ne\ntw", doc.selectedText());
        doc.left();
        assertTrue(!doc.hasSelection(), "a plain move drops the selection");
        doc.selectAll();
        assertEquals("one\ntwo\nthree", doc.selectedText());
    }

    @Test
    void typing_replacesTheSelectionAndDeleteRemovesIt() {
        doc.setText("hello world");
        doc.select(0, 0, 0, 5);
        doc.insert('H');
        assertEquals("H world", doc.text());
        assertEquals(1, doc.cursorCol());
        doc.select(0, 1, 0, 7);
        doc.delete();
        assertEquals("H", doc.text());
        doc.setText("a\nb\nc");
        doc.select(0, 1, 2, 0);
        doc.backspace();
        assertEquals("ac", doc.text());
        assertEquals(0, doc.cursorLine());
        assertEquals(1, doc.cursorCol());
    }

    @Test
    void undo_takesBackARunOfTypingAndRedoPutsItBack() {
        doc.setText("");
        doc.insert('a');
        doc.insert('b');
        doc.insert('c');
        doc.newline();
        doc.insert('d');
        assertEquals("abc\nd", doc.text());
        assertTrue(doc.undo());
        assertEquals("abc\n", doc.text(), "the letter typed after the newline is one step");
        assertTrue(doc.undo());
        assertEquals("abc", doc.text(), "the newline is the next");
        assertTrue(doc.undo());
        assertEquals("", doc.text(), "and the run of typing before it is one step, not three");
        assertTrue(!doc.undo(), "nothing left to take back");
        assertTrue(doc.redo());
        assertEquals("abc", doc.text());
        doc.insert('x');
        assertTrue(!doc.redo(), "a new change forgets what could be put back");
        assertEquals("abcx", doc.text());
    }

    @Test
    void newlineIndented_keepsTheDepthAndOpensABlock() {
        doc.setText("    if (x) {");
        doc.setCursor(0, 12);
        doc.newlineIndented(4);
        assertEquals("    if (x) {\n        ", doc.text());
        assertEquals(8, doc.cursorCol());
        doc.setText("    foo();");
        doc.setCursor(0, 10);
        doc.newlineIndented(4);
        assertEquals("    foo();\n    ", doc.text());
        // A closing brace under the caret moves to its own line at the block's depth.
        doc.setText("    if (x) {}");
        doc.setCursor(0, 12);
        doc.newlineIndented(4);
        assertEquals("    if (x) {\n        \n    }", doc.text());
        assertEquals(1, doc.cursorLine());
        assertEquals(8, doc.cursorCol());
    }

    @Test
    void indent_andOutdentMoveEverySelectedLine() {
        doc.setText("a\nb\nc");
        doc.select(0, 0, 1, 1);
        doc.indent(4);
        assertEquals("    a\n    b\nc", doc.text());
        assertEquals(5, doc.cursorCol());
        doc.outdent(4);
        assertEquals("a\nb\nc", doc.text());
        doc.clearSelection();
        doc.setCursor(2, 0);
        doc.indent(2);
        assertEquals("a\nb\n  c", doc.text());
        doc.select(0, 0, 2, 3);
        doc.toggleLinePrefix("// ");
        assertEquals("// a\n// b\n  // c", doc.text());
        doc.toggleLinePrefix("// ");
        assertEquals("a\nb\n  c", doc.text());
    }

    @Test
    void home_goesToTheTextThenToTheLineStart() {
        doc.setText("    x = 1;");
        doc.setCursor(0, 9);
        doc.home(false);
        assertEquals(4, doc.cursorCol());
        doc.home(false);
        assertEquals(0, doc.cursorCol());
        doc.end(true);
        assertEquals("    x = 1;", doc.selectedText());
    }
}
