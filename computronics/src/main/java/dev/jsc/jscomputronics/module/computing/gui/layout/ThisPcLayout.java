/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;

/**
 * The geometry of the This PC window, in desktop units: a machine card, then a scrolling page of
 * sections (drives, hardware, programs), each a header row over rows of its own height. A drive row
 * carries up to three buttons on its right; the text beside them must never run under them, which is
 * what the layout test proves.
 */
public final class ThisPcLayout {

    public static final int DEFAULT_W = 300;
    public static final int DEFAULT_H = 210;
    public static final int MIN_W = 244;
    public static final int MIN_H = 140;

    /** The machine card at the top: name and kind, then the system and network lines. */
    public static final int CARD_H = 30;
    public static final int CARD_ICON_W = 22;
    /** A section header. */
    public static final int HEADER_H = 12;
    /** A drive or disk row: two lines of text, a usage bar, buttons. */
    public static final int DRIVE_ROW_H = 24;
    /** A hardware key/value line. */
    public static final int KV_ROW_H = 10;
    /** A program cell in the grid. */
    public static final int PROG_CELL_W = 58;
    public static final int PROG_CELL_H = 30;
    public static final int BTN_W = 34;
    public static final int BTN_H = 11;
    public static final int BTN_GAP = 2;
    /** The row icon on the left of a drive row. */
    public static final int ROW_ICON_W = 14;
    public static final int TEXT_X = 22;

    private ThisPcLayout() {
    }

    /** The x of button {@code i} of {@code n} on a drive row, right-aligned from the window edge. */
    public static int buttonX(final int width, final int i, final int n) {
        return width - 3 - (n - i) * (BTN_W + BTN_GAP) + BTN_GAP;
    }

    /** The widest the text beside {@code n} buttons may be before it runs under them. */
    public static int driveTextMaxW(final int width, final int n) {
        return (n == 0 ? width - 4 : buttonX(width, 0, n) - 3) - TEXT_X;
    }

    /** How many program cells fit across the window. */
    public static int programColumns(final int width) {
        return Math.max(1, (width - 8) / PROG_CELL_W);
    }

    /** The x of program cell {@code column}. */
    public static int programCellX(final int width, final int column) {
        return 4 + column * PROG_CELL_W;
    }

    /** The page below the card, where the sections scroll. */
    public static int pageY() {
        return CARD_H + 1;
    }

    public static int pageH(final int height) {
        return height - pageY();
    }

    /** One drive row with its {@code buttons}, as solid boxes, so a test proves the text and the buttons never meet. */
    public static GuiLayout driveRow(final int width, final int buttons) {
        final GuiLayout l = new GuiLayout(width, DRIVE_ROW_H);
        l.box("icon", 4, 3, ROW_ICON_W, 10);
        l.box("text", TEXT_X, 1, driveTextMaxW(width, buttons), DRIVE_ROW_H - 2);
        for (int i = 0; i < buttons; i++) {
            l.box("button" + i, buttonX(width, i, buttons), (DRIVE_ROW_H - BTN_H) / 2, BTN_W, BTN_H);
        }
        return l;
    }

    /** The window's fixed pieces at {@code width} x {@code height}: the card and the page under it. */
    public static GuiLayout layout(final int width, final int height) {
        final GuiLayout l = new GuiLayout(width, height);
        l.box("card-icon", 4, 4, CARD_ICON_W, CARD_H - 8);
        l.box("card-text", 4 + CARD_ICON_W + 4, 2, width - (4 + CARD_ICON_W + 4) - 4, CARD_H - 4);
        l.box("page", 0, pageY(), width, pageH(height));
        return l;
    }

    /** The program grid at {@code width} with {@code count} cells, so a test proves the cells tile without touching. */
    public static GuiLayout programGrid(final int width, final int count) {
        final int columns = programColumns(width);
        final int rows = (count + columns - 1) / columns;
        final GuiLayout l = new GuiLayout(width, Math.max(1, rows) * PROG_CELL_H);
        for (int i = 0; i < count; i++) {
            l.box("cell" + i, programCellX(width, i % columns), (i / columns) * PROG_CELL_H,
                    PROG_CELL_W - 2, PROG_CELL_H - 2);
        }
        return l;
    }
}
