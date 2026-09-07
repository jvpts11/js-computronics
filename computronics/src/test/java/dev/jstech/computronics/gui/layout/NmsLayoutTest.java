/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

class NmsLayoutTest {

    @Test
    void layout_hasNoOverlapsOrOverflow() {
        final GuiLayout l = NmsLayout.layout();
        assertTrue(l.overlaps().isEmpty(), "overlaps: " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "out of bounds: " + l.outOfBounds());
    }

    @Test
    void regions_tileTheWindowHeightWithoutGaps() {
        // title + menu + toolbar + body + status add up to exactly the window height.
        assertEquals(NmsLayout.HEIGHT,
                NmsLayout.TITLE_H + NmsLayout.MENU_H + NmsLayout.TOOLBAR_H + NmsLayout.BODY_H
                        + NmsLayout.STATUS_H);
    }

    @Test
    void bodyColumns_tileTheWindowWidthWithoutGaps() {
        // explorer + vertical splitter + right pane span the full width.
        assertEquals(NmsLayout.WIDTH, NmsLayout.EXPLORER_W + NmsLayout.VSPLIT_W + NmsLayout.RIGHT_W);
    }

    @Test
    void rightPane_stacksToTheBodyHeight() {
        // query tabs + editor + horizontal splitter + results tabs + grid fill the body exactly.
        assertEquals(NmsLayout.BODY_H,
                NmsLayout.TABS_H + NmsLayout.EDITOR_H + NmsLayout.HSPLIT_H + NmsLayout.RES_TABS_H
                        + NmsLayout.GRID_H);
    }

    @Test
    void editor_isTheTallestBodyRegion() {
        // The query editor must dominate the screen: taller than the grid and every other body strip.
        assertTrue(NmsLayout.EDITOR_H > NmsLayout.GRID_H, "editor must be taller than the grid");
        assertTrue(NmsLayout.EDITOR_H > NmsLayout.BODY_H / 2, "editor must take the majority of the body");
    }

    @Test
    void window_staysWithinACompactScreenBudget() {
        // Usable but not screen-filling: comfortably fits common GUI scales (height the tighter axis).
        assertTrue(NmsLayout.HEIGHT <= 240, "height should stay within a usable budget");
        assertTrue(NmsLayout.WIDTH <= 380, "width should stay within a usable budget");
    }

    // File menu elements

    @Test
    void fileDropdown_fitsInWindowAndDoesNotOverlapMenuBar() {
        final GuiLayout l = NmsLayout.dropdownOpen();
        assertTrue(l.overlaps().isEmpty(), "dropdown overlaps: " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "dropdown out of bounds: " + l.outOfBounds());
    }

    @Test
    void fileDropdown_sitsImmediatelyBelowMenuBar() {
        // The dropdown top edge must be exactly at the menu bar's bottom edge (no gap, no overlap).
        assertEquals(NmsLayout.MENU_Y + NmsLayout.MENU_H, NmsLayout.FILE_DROP_Y,
                "dropdown must start exactly where the menu bar ends");
    }

    @Test
    void fileDropdown_staysWithinWindow() {
        // The dropdown overlays the body when open, and it must not extend past the window's bottom edge.
        assertTrue(NmsLayout.FILE_DROP_Y + NmsLayout.FILE_DROP_H <= NmsLayout.HEIGHT,
                "dropdown bottom must not exceed the window height");
        assertTrue(NmsLayout.FILE_DROP_X + NmsLayout.FILE_DROP_W <= NmsLayout.WIDTH,
                "dropdown right edge must not exceed the window width");
    }

    @Test
    void saveDialog_fitsInWindowAndHasNoOverlaps() {
        final GuiLayout l = NmsLayout.saveDialogOpen();
        assertTrue(l.overlaps().isEmpty(), "save dialog overlaps: " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "save dialog out of bounds: " + l.outOfBounds());
    }

    @Test
    void saveDialog_isCentredAndClearsMenuBar() {
        // The dialog must start below the menu bar so it does not paint over it.
        assertTrue(NmsLayout.DIALOG_SAVE_Y > NmsLayout.MENU_Y + NmsLayout.MENU_H,
                "Save-As dialog must start below the menu bar");
        // The EditBox must sit within the dialog panel.
        assertTrue(NmsLayout.SAVE_EDIT_X >= NmsLayout.DIALOG_X,
                "EditBox must not start left of the dialog");
        assertTrue(NmsLayout.SAVE_EDIT_X + NmsLayout.SAVE_EDIT_W <= NmsLayout.DIALOG_X + NmsLayout.DIALOG_W,
                "EditBox must not extend past the dialog's right edge");
        assertTrue(NmsLayout.SAVE_EDIT_Y >= NmsLayout.DIALOG_SAVE_Y,
                "EditBox must not start above the dialog");
        assertTrue(NmsLayout.SAVE_EDIT_Y + NmsLayout.SAVE_EDIT_H
                        <= NmsLayout.DIALOG_SAVE_Y + NmsLayout.DIALOG_H_SAVE,
                "EditBox must not extend past the dialog's bottom edge");
    }

    @Test
    void openPicker_fitsInWindowAndHasNoOverlaps() {
        final GuiLayout l = NmsLayout.openPickerOpen();
        assertTrue(l.overlaps().isEmpty(), "open picker overlaps: " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "open picker out of bounds: " + l.outOfBounds());
    }

    @Test
    void openPicker_cancelButtonDoesNotOverlapLastPickerRow() {
        // The cancel button sits below all picker rows.
        final int lastRowBottom = NmsLayout.DIALOG_OPEN_Y
                + NmsLayout.PICKER_LIST_OFFSET_Y
                + NmsLayout.PICKER_VISIBLE * NmsLayout.PICKER_ROW_H;
        final int cancelTop = NmsLayout.DIALOG_OPEN_Y + NmsLayout.CANCEL_BTN_REL_Y;
        assertTrue(cancelTop >= lastRowBottom,
                "cancel button must start at or below the last picker row's bottom edge");
    }

    @Test
    void openPicker_isCentredAndClearsMenuBar() {
        assertTrue(NmsLayout.DIALOG_OPEN_Y > NmsLayout.MENU_Y + NmsLayout.MENU_H,
                "Open picker dialog must start below the menu bar");
        assertTrue(NmsLayout.DIALOG_OPEN_Y + NmsLayout.DIALOG_H_OPEN <= NmsLayout.HEIGHT,
                "Open picker dialog must not exceed the window height");
    }
}
