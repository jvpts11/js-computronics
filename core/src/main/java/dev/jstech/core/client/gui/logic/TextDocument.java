/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * The text of a multi-line editor and the caret in it: lines that grow as characters are typed, split at
 * the caret on Enter and join back on a Backspace at a line's start, with the caret moving by character or
 * by line and never leaving the text. Pure, so the editing rules are tested without a screen.
 */
public final class TextDocument {

    private final List<StringBuilder> lines = new ArrayList<>();
    private int line;
    private int col;

    public TextDocument() {
        lines.add(new StringBuilder());
    }

    /** Replaces the whole text and puts the caret at the start. */
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

    /** Puts the caret at the position, pulled inside the text where it points past it. */
    public void setCursor(final int targetLine, final int targetCol) {
        line = Math.max(0, Math.min(lines.size() - 1, targetLine));
        col = Math.max(0, Math.min(lines.get(line).length(), targetCol));
    }

    public void insert(final char c) {
        lines.get(line).insert(col, c);
        col++;
    }

    /** Types a whole string at the caret, a newline in it splitting the line the way Enter does. */
    public void insertText(final String text) {
        for (final char c : text.toCharArray()) {
            if (c == '\n') {
                newline();
            } else if (c != '\r') {
                insert(c);
            }
        }
    }

    /**
     * Moves the caret to the next place {@code needle} occurs after it, going round to the top when
     * nothing follows, and says whether it was found anywhere. The caret lands at the match's start,
     * which is where an editor scrolls to show it.
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
                line = at;
                col = hit;
                return true;
            }
        }
        return false;
    }

    /**
     * Puts {@code marker} at the start of the caret's line, or takes it off when it is already there,
     * which is what commenting a line out and back in means to an editor.
     */
    public void toggleLinePrefix(final String marker) {
        final StringBuilder cur = lines.get(line);
        final String text = cur.toString();
        final int indent = text.length() - text.stripLeading().length();
        if (text.startsWith(marker, indent)) {
            cur.delete(indent, indent + marker.length());
            col = Math.max(0, col - marker.length());
        } else {
            cur.insert(indent, marker);
            col += marker.length();
        }
        col = Math.min(col, cur.length());
    }

    /** Splits the current line at the caret; the caret starts the new line. */
    public void newline() {
        final StringBuilder cur = lines.get(line);
        final String tail = cur.substring(col);
        cur.delete(col, cur.length());
        lines.add(line + 1, new StringBuilder(tail));
        line++;
        col = 0;
    }

    /** Deletes the character before the caret, or joins the line onto the previous one at a line's start. */
    public void backspace() {
        if (col > 0) {
            lines.get(line).deleteCharAt(col - 1);
            col--;
        } else if (line > 0) {
            final StringBuilder prev = lines.get(line - 1);
            col = prev.length();
            prev.append(lines.remove(line));
            line--;
        }
    }

    public void left() {
        if (col > 0) {
            col--;
        } else if (line > 0) {
            line--;
            col = lines.get(line).length();
        }
    }

    public void right() {
        if (col < lines.get(line).length()) {
            col++;
        } else if (line < lines.size() - 1) {
            line++;
            col = 0;
        }
    }

    public void up() {
        if (line > 0) {
            line--;
            col = Math.min(col, lines.get(line).length());
        }
    }

    public void down() {
        if (line < lines.size() - 1) {
            line++;
            col = Math.min(col, lines.get(line).length());
        }
    }
}
