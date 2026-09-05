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
 * The vertical layout of the Pattern Studio window: the tab strip, the editor, the player's inventory band
 * and the action bar, top to bottom. Pure arithmetic, so a test can prove the band fits in the window a
 * standard monitor opens the program in.
 *
 * <p>The editor's minimum is the tallest tab (the machine editor: three rows of cells and the name row); the
 * band is the vanilla inventory shape, three rows, a gap and the hotbar, inside a two-pixel frame. When the
 * content is shorter than the band needs, the band folds away and the editor runs down to the bar; the
 * "Inventory" label above the band is the first thing to go, so the band itself survives a tight window.
 */
public final class PatternStudioLayout {

    public static final int TAB_H = 13;
    public static final int PAD = 3;
    public static final int CELL = 18;
    public static final int FIELD_H = 12;
    public static final int BAR_H = 22;
    public static final int PROC_COLS = 3;
    public static final int PROC_ROWS = 3;
    /** The tallest editor: the machine tab's grids and the name row under them. */
    public static final int EDITOR_MIN_H = PAD + PROC_ROWS * CELL + PAD + FIELD_H + PAD;

    /** The label above the inventory band, shown only when the window has room to spare. */
    public static final int BAND_LABEL_H = 10;
    public static final int INV_COLS = 9;
    public static final int INV_ROWS = 4;
    public static final int HOTBAR_GAP = 4;
    public static final int BAND_PAD = 2;
    public static final int BAND_H = BAND_PAD * 2 + INV_ROWS * CELL + HOTBAR_GAP;
    public static final int BAND_W = BAND_PAD * 2 + INV_COLS * CELL;

    private PatternStudioLayout() {
    }

    /** The content height below which the band folds away: tabs, the tallest editor, the band and the bar. */
    public static int minContentHeight() {
        return TAB_H + EDITOR_MIN_H + BAND_H + BAR_H;
    }

    /** Whether the band fits under the editor in a content area {@code contentHeight} tall. */
    public static boolean bandVisible(final int contentHeight) {
        return contentHeight >= minContentHeight();
    }

    /** Whether the label above the band fits as well. */
    public static boolean bandLabelVisible(final int contentHeight) {
        return contentHeight >= minContentHeight() + BAND_LABEL_H;
    }

    /** The content-local top of the band frame; with the band folded, the bar's top (the editor ends there). */
    public static int bandTop(final int contentHeight) {
        return bandVisible(contentHeight) ? contentHeight - BAR_H - BAND_H : contentHeight - BAR_H;
    }

    /** The content-local bottom of the editor: above the band's label, the band, or the bar. */
    public static int editorBottom(final int contentHeight) {
        return bandTop(contentHeight) - (bandLabelVisible(contentHeight) ? BAND_LABEL_H : 0);
    }

    /** The y of inventory row {@code row} inside the band frame; the hotbar sits a gap below the main rows. */
    public static int rowYOffset(final int row) {
        return row * CELL + (row >= 3 ? HOTBAR_GAP : 0);
    }

    /** The solid regions of a window {@code contentWidth} by {@code contentHeight}, for the overlap check. */
    public static GuiLayout layout(final int contentWidth, final int contentHeight) {
        final GuiLayout layout = new GuiLayout(contentWidth, contentHeight)
                .box("tabs", 0, 0, contentWidth, TAB_H)
                .box("editor", 0, TAB_H, contentWidth, editorBottom(contentHeight) - TAB_H)
                .box("bar", 0, contentHeight - BAR_H, contentWidth, BAR_H);
        if (bandVisible(contentHeight)) {
            layout.box("band", PAD, bandTop(contentHeight), BAND_W, BAND_H);
        }
        return layout;
    }
}
