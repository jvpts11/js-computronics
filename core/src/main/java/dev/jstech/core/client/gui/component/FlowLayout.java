/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

/**
 * A cursor that hands out rectangles one after another along a row or a column, with a gap between them:
 * how a bar of buttons or a stack of fields is laid out without arithmetic in the caller. Pure, so a test
 * can prove where things land.
 */
public final class FlowLayout {

    private final boolean horizontal;
    private final int gap;
    private int cursorX;
    private int cursorY;

    private FlowLayout(final boolean horizontal, final int x, final int y, final int gap) {
        this.horizontal = horizontal;
        this.gap = gap;
        this.cursorX = x;
        this.cursorY = y;
    }

    /** A row starting at ({@code x}, {@code y}), each rectangle {@code gap} pixels after the previous one. */
    public static FlowLayout row(final int x, final int y, final int gap) {
        return new FlowLayout(true, x, y, gap);
    }

    /** A column starting at ({@code x}, {@code y}), each rectangle {@code gap} pixels below the previous one. */
    public static FlowLayout column(final int x, final int y, final int gap) {
        return new FlowLayout(false, x, y, gap);
    }

    /** The next rectangle, {@code w} by {@code h}, and advances past it. */
    public int[] next(final int w, final int h) {
        final int[] rect = {cursorX, cursorY, w, h};
        if (horizontal) {
            cursorX += w + gap;
        } else {
            cursorY += h + gap;
        }
        return rect;
    }

    /** Places {@code component} in the next rectangle and returns it. */
    public <T extends UiComponent> T place(final T component, final int w, final int h) {
        final int[] rect = next(w, h);
        component.setBounds(rect[0], rect[1], rect[2], rect[3]);
        return component;
    }

    /** Leaves {@code amount} pixels empty before the next rectangle. */
    public FlowLayout skip(final int amount) {
        if (horizontal) {
            cursorX += amount;
        } else {
            cursorY += amount;
        }
        return this;
    }

    /** Where the next rectangle starts along the row. */
    public int x() {
        return cursorX;
    }

    /** Where the next rectangle starts down the column. */
    public int y() {
        return cursorY;
    }
}
