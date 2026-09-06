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
