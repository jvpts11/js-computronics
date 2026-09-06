/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A small multi-line text editor for the Network Management Studio's query pane: the stock {@code EditBox} is
 * single-line, and the Studio wants a real editor where {@code ENTER} breaks a line and the IQL is syntax-coloured
 * (keywords blue, strings red, the rest near-black — the classic SSMS palette). It keeps the text as a list of lines
 * with a cursor; the screen drives it (key/char events, the caret blink tick) and asks it to paint into the editor
 * rectangle. No selection or horizontal scroll — a long line is simply broken with {@code ENTER}.
 */
final class IqlEditor {

    private static final int SYN_KEYWORD = 0xFF0000FF;
    private static final int SYN_STRING = 0xFFA31515;
    private static final int SYN_TEXT = 0xFF1E1E1E;
    private static final int CARET = 0xFF1E1E1E;
    private static final Set<String> KEYWORDS = Set.of(
            "QUERY", "SELECT", "INSERT", "DELETE", "MOVE", "DROP", "CRAFT", "COUNT", "LOCK", "UNLOCK",
            "ANALYZE", "VACUUM", "REINDEX", "SHOW", "FROM", "TO", "WHERE", "IF", "ORDER", "BY", "LIMIT",
            "ASC", "DESC", "AND", "OR", "NOT", "ALL", "CONTAINS", "HAS", "LIKE", "IN",
            // Layer 2: saved objects and triggers.
            "CREATE", "VIEW", "PROCEDURE", "PROC", "JOB", "AS", "EVERY", "WHEN", "EXEC", "CALL");

    private final Font font;
    private final int maxLength;
    private final List<String> lines = new ArrayList<>();
    private int cursorLine;
    private int cursorCol;
    private int scroll;
    private int caretTimer;

    IqlEditor(final Font font, final int maxLength) {
        this.font = font;
        this.maxLength = maxLength;
        lines.add("");
    }

    /** The whole text, lines joined by newlines — what gets sent to the server. */
    String value() {
        return String.join("\n", lines);
    }

    boolean isBlank() {
        for (final String line : lines) {
            if (!line.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** Replaces the whole text — used to restore a persisted script into a freshly opened, empty editor. */
    void setValue(final String text) {
        lines.clear();
        if (text == null || text.isEmpty()) {
            lines.add("");
        } else {
            for (final String line : text.split("\n", -1)) {
                lines.add(line);
            }
        }
        cursorLine = 0;
        cursorCol = 0;
        scroll = 0;
    }

    /** Advances the caret blink; the screen calls this each container tick. */
    void tick() {
        caretTimer++;
    }

    /** The 0-based index of the first visible line, so the screen can number the gutter to match. */
    int scrollLine() {
        return scroll;
    }

    /** The number of lines, so the gutter only numbers real lines. */
    int lineCount() {
        return lines.size();
    }

    private int length() {
        int n = lines.size() - 1; // the newlines
        for (final String line : lines) {
            n += line.length();
        }
        return n;
    }

    /** Inserts a whole string at the cursor (used when the Object Explorer injects a name). */
    void insert(final String s) {
        for (int k = 0; k < s.length(); k++) {
            insert(s.charAt(k));
        }
    }

    void insert(final char c) {
        if ((c < ' ' && c != '\t') || length() >= maxLength) {
            return;
        }
        final String line = lines.get(cursorLine);
        lines.set(cursorLine, line.substring(0, cursorCol) + c + line.substring(cursorCol));
        cursorCol++;
        caretTimer = 0;
    }

    /** Handles an editing key; returns true when it consumed the key. */
    boolean keyPressed(final int key) {
        caretTimer = 0;
        switch (key) {
            case 257, 335 -> newline();   // Enter / numpad Enter
            case 259 -> backspace();      // Backspace
            case 261 -> delete();         // Delete
            case 263 -> left();           // Left
            case 262 -> right();          // Right
            case 265 -> up();             // Up
            case 264 -> down();           // Down
            case 268 -> cursorCol = 0;    // Home
            case 269 -> cursorCol = lines.get(cursorLine).length(); // End
            default -> {
                return false;
            }
        }
        return true;
    }

    private void newline() {
        if (length() >= maxLength) {
            return;
        }
        final String line = lines.get(cursorLine);
        lines.set(cursorLine, line.substring(0, cursorCol));
        lines.add(cursorLine + 1, line.substring(cursorCol));
        cursorLine++;
        cursorCol = 0;
    }

    private void backspace() {
        if (cursorCol > 0) {
            final String line = lines.get(cursorLine);
            lines.set(cursorLine, line.substring(0, cursorCol - 1) + line.substring(cursorCol));
            cursorCol--;
        } else if (cursorLine > 0) {
            final String prev = lines.get(cursorLine - 1);
            cursorCol = prev.length();
            lines.set(cursorLine - 1, prev + lines.remove(cursorLine));
            cursorLine--;
        }
    }

    private void delete() {
        final String line = lines.get(cursorLine);
        if (cursorCol < line.length()) {
            lines.set(cursorLine, line.substring(0, cursorCol) + line.substring(cursorCol + 1));
        } else if (cursorLine < lines.size() - 1) {
            lines.set(cursorLine, line + lines.remove(cursorLine + 1));
        }
    }

    private void left() {
        if (cursorCol > 0) {
            cursorCol--;
        } else if (cursorLine > 0) {
            cursorLine--;
            cursorCol = lines.get(cursorLine).length();
        }
    }

    private void right() {
        if (cursorCol < lines.get(cursorLine).length()) {
            cursorCol++;
        } else if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = 0;
        }
    }

    private void up() {
        if (cursorLine > 0) {
            cursorLine--;
            cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
        }
    }

    private void down() {
        if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
        }
    }

    /**
     * Draws the visible lines (syntax-coloured) and the blinking caret at {@code (x, y)}, {@code maxRows} tall at
     * {@code lineHeight} pitch. Scrolls vertically to keep the cursor in view.
     */
    void render(final GuiGraphics g, final int x, final int y, final int lineHeight, final int maxRows) {
        if (cursorLine < scroll) {
            scroll = cursorLine;
        } else if (cursorLine >= scroll + maxRows) {
            scroll = cursorLine - maxRows + 1;
        }
        for (int i = 0; i < maxRows && scroll + i < lines.size(); i++) {
            final String line = lines.get(scroll + i);
            final int ly = y + i * lineHeight;
            drawHighlighted(g, line, x, ly);
            if (scroll + i == cursorLine && (caretTimer / 6) % 2 == 0) {
                final int caretX = x + font.width(line.substring(0, Math.min(cursorCol, line.length())));
                g.fill(caretX, ly - 1, caretX + 1, ly + font.lineHeight, CARET);
            }
        }
    }

    private void drawHighlighted(final GuiGraphics g, final String line, final int x, final int y) {
        int cx = x;
        int i = 0;
        while (i < line.length()) {
            final boolean space = Character.isWhitespace(line.charAt(i));
            int j = i;
            while (j < line.length() && Character.isWhitespace(line.charAt(j)) == space) {
                j++;
            }
            final String run = line.substring(i, j);
            if (!space) {
                g.drawString(font, run, cx, y, colorFor(run), false);
            }
            cx += font.width(run);
            i = j;
        }
    }

    private static int colorFor(final String word) {
        if (word.startsWith("\"")) {
            return SYN_STRING;
        }
        if (KEYWORDS.contains(word.toUpperCase(Locale.ROOT))) {
            return SYN_KEYWORD;
        }
        return SYN_TEXT;
    }
}
