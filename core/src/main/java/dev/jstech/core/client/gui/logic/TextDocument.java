/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The text of a multi-line editor, the caret in it, and what is selected.
 *
 * <p>Lines grow as characters are typed, split at the caret on Enter and join back on a Backspace at a
 * line's start; the caret moves by character or by line and never leaves the text. A selection is the
 * stretch between an anchor and the caret: moving with the extend flag on grows it, typing replaces
 * it, and every change can be taken back with undo. Pure, so the editing rules are tested without a
 * screen.
 */
public final class TextDocument {

    /** How many changes can be taken back. */
    private static final int UNDO_DEPTH = 200;

    private final List<StringBuilder> lines = new ArrayList<>();
    private int line;
    private int col;
    /** Where the selection was started, or -1 for none; the other end is the caret. */
    private int anchorLine = -1;
    private int anchorCol;

    /** A place in the text: a line and a column on it. */
    public record Spot(int line, int col) {
        /** Whether this comes before {@code other} in reading order. */
        public boolean before(final Spot other) {
            return line < other.line || line == other.line && col < other.col;
        }
    }

    /** What the text was before a change, and where the caret stood, so the change can be taken back. */
    private record Snapshot(String text, int line, int col) {
    }

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    /** The kind of the last change recorded, so a run of typing is one step to take back. */
    private String lastEdit = "";

    public TextDocument() {
        lines.add(new StringBuilder());
    }

    /** Replaces the whole text and puts the caret at the start, forgetting what could be undone. */
    public void setText(final String text) {
        lines.clear();
        for (final String part : text.split("\n", -1)) {
            lines.add(new StringBuilder(part));
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
        line = 0;
        col = 0;
        anchorLine = -1;
        undo.clear();
        redo.clear();
        lastEdit = "";
    }

    /** The whole text, lines joined by newlines. */
    public String text() {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines.get(i));
        }
        return out.toString();
    }

    public int lineCount() {
        return lines.size();
    }

    public String line(final int index) {
        return lines.get(index).toString();
    }

    public int cursorLine() {
        return line;
    }

    public int cursorCol() {
        return col;
    }

    /** Puts the caret at the position, pulled inside the text where it points past it, dropping any selection. */
    public void setCursor(final int targetLine, final int targetCol) {
        setCursor(targetLine, targetCol, false);
    }

    /** The same, growing the selection from where the caret was when {@code extend} is set. */
    public void setCursor(final int targetLine, final int targetCol, final boolean extend) {
        beginMove(extend);
        line = Math.max(0, Math.min(lines.size() - 1, targetLine));
        col = Math.max(0, Math.min(lines.get(line).length(), targetCol));
    }

    /* Selection */

    /** Whether something is selected. */
    public boolean hasSelection() {
        return anchorLine >= 0 && !(anchorLine == line && anchorCol == col);
    }

    /** Where the selection starts, in reading order, or the caret when nothing is selected. */
    public Spot selectionStart() {
        final Spot caret = new Spot(line, col);
        if (!hasSelection()) {
            return caret;
        }
        final Spot anchor = new Spot(anchorLine, anchorCol);
        return anchor.before(caret) ? anchor : caret;
    }

    /** Where the selection ends, in reading order, or the caret when nothing is selected. */
    public Spot selectionEnd() {
        final Spot caret = new Spot(line, col);
        if (!hasSelection()) {
            return caret;
        }
        final Spot anchor = new Spot(anchorLine, anchorCol);
        return anchor.before(caret) ? caret : anchor;
    }

    /** Selects from one place to another, the caret landing at the second. */
    public void select(final int fromLine, final int fromCol, final int toLine, final int toCol) {
        anchorLine = Math.max(0, Math.min(lines.size() - 1, fromLine));
        anchorCol = Math.max(0, Math.min(lines.get(anchorLine).length(), fromCol));
        line = Math.max(0, Math.min(lines.size() - 1, toLine));
        col = Math.max(0, Math.min(lines.get(line).length(), toCol));
    }

    public void selectAll() {
        select(0, 0, lines.size() - 1, lines.get(lines.size() - 1).length());
    }

    /** Selects the whole of the caret's line, which is what a triple click and Ctrl+L mean. */
    public void selectLine() {
        select(line, 0, line, lines.get(line).length());
    }

    public void clearSelection() {
        anchorLine = -1;
    }

    /** What is selected, lines joined by newlines, or empty. */
    public String selectedText() {
        if (!hasSelection()) {
            return "";
        }
        final Spot from = selectionStart();
        final Spot to = selectionEnd();
        if (from.line() == to.line()) {
            return lines.get(from.line()).substring(from.col(), to.col());
        }
        final StringBuilder out = new StringBuilder(lines.get(from.line()).substring(from.col()));
        for (int i = from.line() + 1; i < to.line(); i++) {
            out.append('\n').append(lines.get(i));
        }
        out.append('\n').append(lines.get(to.line()), 0, to.col());
        return out.toString();
    }

    /** Removes what is selected, the caret landing where it was; false when nothing was selected. */
    public boolean deleteSelection() {
        if (!hasSelection()) {
            return false;
        }
        remember("delete");
        final Spot from = selectionStart();
        final Spot to = selectionEnd();
        final StringBuilder first = lines.get(from.line());
        final String tail = lines.get(to.line()).substring(to.col());
        first.delete(from.col(), first.length());
        for (int i = to.line(); i > from.line(); i--) {
            lines.remove(i);
        }
        first.append(tail);
        line = from.line();
        col = from.col();
        anchorLine = -1;
        return true;
    }

    /** Sets the anchor before a move that extends, or drops the selection before one that does not. */
    private void beginMove(final boolean extend) {
        if (extend) {
            if (anchorLine < 0) {
                anchorLine = line;
                anchorCol = col;
            }
        } else {
            anchorLine = -1;
        }
    }

    /* Undo */

    /**
     * Records the text as it stands so the change about to be made can be taken back.
     *
     * <p>Changes of one kind in a row (typing letter after letter, deleting one after another) are one
     * step, so undo takes back a word rather than a keystroke; a change of kind, or a move, starts a
     * new step.
     */
    private void remember(final String kind) {
        if (!kind.equals(lastEdit) || undo.isEmpty()) {
            undo.push(new Snapshot(text(), line, col));
            while (undo.size() > UNDO_DEPTH) {
                undo.removeLast();
            }
        }
        lastEdit = kind;
        redo.clear();
    }

    /** Ends the current run of changes, so the next one starts a step of its own. */
    public void breakUndo() {
        lastEdit = "";
    }

    /** Takes the last change back; false when there is nothing to take back. */
    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(new Snapshot(text(), line, col));
        restore(undo.pop());
        lastEdit = "";
        return true;
    }

    /** Puts back what undo took; false when there is nothing to put back. */
    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(new Snapshot(text(), line, col));
        restore(redo.pop());
        lastEdit = "";
        return true;
    }

    private void restore(final Snapshot snapshot) {
        lines.clear();
        for (final String part : snapshot.text().split("\n", -1)) {
            lines.add(new StringBuilder(part));
        }
        anchorLine = -1;
        setCursor(snapshot.line(), snapshot.col(), false);
    }

    /* Editing */

    /** Types a character at the caret, in place of the selection when there is one. */
    public void insert(final char c) {
        if (!deleteSelection()) {
            remember("type");
        }
        lines.get(line).insert(col, c);
        col++;
    }

    /** Types a whole string at the caret, a newline in it splitting the line the way Enter does. */
    public void insertText(final String text) {
        deleteSelection();
        remember("paste");
        for (final char c : text.toCharArray()) {
            if (c == '\n') {
                splitLine();
            } else if (c != '\r') {
                lines.get(line).insert(col, c);
                col++;
            }
        }
    }

    /**
     * Moves the caret to the next place {@code needle} occurs after it, going round to the top when
     * nothing follows, and says whether it was found anywhere. The caret lands at the match's start,
     * which is where an editor scrolls to show it, with the match selected.
     */
    public boolean find(final String needle) {
        if (needle == null || needle.isEmpty()) {
            return false;
        }
        final int count = lines.size();
        for (int step = 0; step <= count; step++) {
            final int at = (line + step) % count;
            final String text = lines.get(at).toString();
            // On the caret's own line the search starts after the caret, so repeating moves on.
            final int from = step == 0 ? col + 1 : 0;
            final int hit = from <= text.length() ? text.indexOf(needle, from) : -1;
            // Back on the caret's line after going round, only what sits at or before the caret is new.
            if (hit >= 0 && !(step == count && hit > col)) {
                select(at, hit + needle.length(), at, hit);
                return true;
            }
        }
        return false;
    }

    /**
     * Puts {@code marker} at the start of every selected line, or takes it off every one of them when the
     * first already has it, which is what commenting a stretch out and back in means to an editor.
     */
    public void toggleLinePrefix(final String marker) {
        remember("comment");
        final int from = selectionStart().line();
        final int to = selectionEnd().line();
        final String first = lines.get(from).toString();
        final boolean adding = !first.startsWith(marker, first.length() - first.stripLeading().length());
        for (int i = from; i <= to; i++) {
            final StringBuilder cur = lines.get(i);
            final String text = cur.toString();
            final int indent = text.length() - text.stripLeading().length();
            if (adding) {
                cur.insert(indent, marker);
                if (i == line) {
                    col += marker.length();
                }
                if (i == anchorLine) {
                    anchorCol += marker.length();
                }
            } else if (text.startsWith(marker, indent)) {
                cur.delete(indent, indent + marker.length());
                if (i == line) {
                    col = Math.max(0, col - marker.length());
                }
                if (i == anchorLine) {
                    anchorCol = Math.max(0, anchorCol - marker.length());
                }
            }
        }
        col = Math.min(col, lines.get(line).length());
    }

    /**
     * Pushes every selected line in by {@code unit} spaces, or the caret's line when nothing is selected.
     */
    public void indent(final int unit) {
        remember("indent");
        final String spaces = " ".repeat(Math.max(1, unit));
        final int from = selectionStart().line();
        final int to = selectionEnd().line();
        for (int i = from; i <= to; i++) {
            lines.get(i).insert(0, spaces);
        }
        col += spaces.length();
        if (anchorLine >= 0) {
            anchorCol += spaces.length();
        }
    }

    /** Pulls every selected line out by up to {@code unit} spaces, or the caret's line when nothing is selected. */
    public void outdent(final int unit) {
        remember("indent");
        final int from = selectionStart().line();
        final int to = selectionEnd().line();
        for (int i = from; i <= to; i++) {
            final StringBuilder cur = lines.get(i);
            int taken = 0;
            while (taken < unit && cur.length() > 0 && cur.charAt(0) == ' ') {
                cur.deleteCharAt(0);
                taken++;
            }
            if (i == line) {
                col = Math.max(0, col - taken);
            }
            if (i == anchorLine) {
                anchorCol = Math.max(0, anchorCol - taken);
            }
        }
    }

    /** Splits the current line at the caret; the caret starts the new line. */
    public void newline() {
        deleteSelection();
        remember("newline");
        splitLine();
    }

    /**
     * Splits the line the way an editor that knows about code does: the new line starts with the old
     * one's indentation, one more step of {@code unit} when the caret follows an opening brace, and a
     * closing brace that sat under the caret is moved to a line of its own below, at the old depth.
     */
    public void newlineIndented(final int unit) {
        deleteSelection();
        remember("newline");
        final String cur = lines.get(line).toString();
        final int indent = cur.length() - cur.stripLeading().length();
        final String before = cur.substring(0, col).stripTrailing();
        final String after = cur.substring(col);
        final boolean opened = before.endsWith("{");
        final boolean closes = opened && after.stripLeading().startsWith("}");
        splitLine();
        final int depth = Math.min(indent, cur.length()) + (opened ? Math.max(1, unit) : 0);
        lines.get(line).insert(0, " ".repeat(depth));
        col = depth;
        if (closes) {
            // The brace goes on its own line under the caret, at the depth of the line that opened it.
            final StringBuilder here = lines.get(line);
            final String rest = here.substring(col).stripLeading();
            here.delete(col, here.length());
            lines.add(line + 1, new StringBuilder(" ".repeat(Math.min(indent, cur.length())) + rest));
        }
    }

    private void splitLine() {
        final StringBuilder cur = lines.get(line);
        final String tail = cur.substring(col);
        cur.delete(col, cur.length());
        lines.add(line + 1, new StringBuilder(tail));
        line++;
        col = 0;
    }

    /**
     * Deletes the character before the caret, or joins the line onto the previous one at a line's start;
     * with something selected, deletes that instead.
     */
    public void backspace() {
        if (deleteSelection()) {
            return;
        }
        if (col > 0) {
            remember("delete");
            lines.get(line).deleteCharAt(col - 1);
            col--;
        } else if (line > 0) {
            remember("delete");
            final StringBuilder prev = lines.get(line - 1);
            col = prev.length();
            prev.append(lines.remove(line));
            line--;
        }
    }

    /**
     * Deletes the character after the caret, or joins the next line onto this one at a line's end; with
     * something selected, deletes that instead.
     */
    public void delete() {
        if (deleteSelection()) {
            return;
        }
        final StringBuilder cur = lines.get(line);
        if (col < cur.length()) {
            remember("delete");
            cur.deleteCharAt(col);
        } else if (line < lines.size() - 1) {
            remember("delete");
            cur.append(lines.remove(line + 1));
        }
    }

    /* Moving */

    public void left() {
        left(false);
    }

    public void right() {
        right(false);
    }

    public void up() {
        up(false);
    }

    public void down() {
        down(false);
    }

    public void left(final boolean extend) {
        beginMove(extend);
        if (col > 0) {
            col--;
        } else if (line > 0) {
            line--;
            col = lines.get(line).length();
        }
    }

    public void right(final boolean extend) {
        beginMove(extend);
        if (col < lines.get(line).length()) {
            col++;
        } else if (line < lines.size() - 1) {
            line++;
            col = 0;
        }
    }

    public void up(final boolean extend) {
        beginMove(extend);
        if (line > 0) {
            line--;
            col = Math.min(col, lines.get(line).length());
        }
    }

    public void down(final boolean extend) {
        beginMove(extend);
        if (line < lines.size() - 1) {
            line++;
            col = Math.min(col, lines.get(line).length());
        }
    }

    /** To the start of the line's text, or its very start when already there, the way Home behaves. */
    public void home(final boolean extend) {
        beginMove(extend);
        final String text = lines.get(line).toString();
        final int indent = text.length() - text.stripLeading().length();
        col = col == indent ? 0 : indent;
    }

    public void end(final boolean extend) {
        beginMove(extend);
        col = lines.get(line).length();
    }

    /** The character after the caret, or 0 at a line's end. */
    public char charAfter() {
        final StringBuilder cur = lines.get(line);
        return col < cur.length() ? cur.charAt(col) : 0;
    }

    /** The character before the caret, or 0 at a line's start. */
    public char charBefore() {
        return col > 0 ? lines.get(line).charAt(col - 1) : 0;
    }
}
