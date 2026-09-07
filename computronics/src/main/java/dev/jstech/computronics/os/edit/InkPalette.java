/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.edit;

/**
 * The colours a piece of source is painted in, one set for a light window and one for a dark one.
 *
 * <p>An editor sits on whatever ground its system gives it, and a colour that reads on cream is a
 * colour that vanishes on slate. So the two sets are written out separately rather than one being
 * derived from the other by arithmetic, which is how the unreadable middle greys got shipped before.
 * Every colour here is checked against its own ground by a test, so a change that makes a comment
 * disappear fails before anyone opens a monitor.
 */
public record InkPalette(int ground, int gutter, int gutterText, int caret, int currentLine,
                         int plain, int keyword, int name, int text, int number, int comment, int symbol) {

    /** For a window with a light client area: the Frames editions and every Linux desktop but a dark one. */
    public static final InkPalette LIGHT = new InkPalette(
            0xFFFFFFFF, 0xFFF2F3F6, 0xFF8A8F9C, 0xFF1B2437, 0xFFF6F7FB,
            0xFF1B2437, 0xFF0033B0, 0xFF1B2437, 0xFF9B1C1C, 0xFF8A4B00, 0xFF2F7A3F, 0xFF505A6B);

    /** For a window with a dark client area: Frames 11 in its dark palette, and a terminal. */
    public static final InkPalette DARK = new InkPalette(
            0xFF1E212A, 0xFF1A1D25, 0xFF7B8494, 0xFFE7E9EF, 0xFF242833,
            0xFFD5DAE4, 0xFF8FA9F5, 0xFFD5DAE4, 0xFFDB9A72, 0xFFD3B475, 0xFF89939F, 0xFF9AA3B2);

    /** The palette for a window whose client area is {@code dark}. */
    public static InkPalette forGround(final boolean dark) {
        return dark ? DARK : LIGHT;
    }

    /** The colour of a run, given what the language said it was. */
    public int of(final CodeRuns.Ink ink) {
        return switch (ink) {
            case PLAIN -> this.plain;
            case KEYWORD -> this.keyword;
            case NAME -> this.name;
            case TEXT -> this.text;
            case NUMBER -> this.number;
            case COMMENT -> this.comment;
            case SYMBOL -> this.symbol;
        };
    }
}
