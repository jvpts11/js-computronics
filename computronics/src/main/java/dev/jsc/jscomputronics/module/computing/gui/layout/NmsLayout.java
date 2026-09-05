/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import dev.jsc.jscomputronics.common.gui.layout.GuiLayout;

/**
 * Pure layout for the Network Management Studio, with no Minecraft dependency so {@link #layout()} is
 * unit-tested. The Studio mirrors a classic SSMS window: a title strip, a menu bar, a toolbar, then the
 * body split into an Object Explorer column on the left and, on the right, a dominant query editor over a
 * smaller results grid, with a blue status bar across the bottom. Two splitter bands (vertical between the
 * explorer and the right pane, horizontal between editor and grid) are where the player will later drag to
 * resize.
 *
 * <p>The validated boxes are the macro regions — they tile the window edge-to-edge and must never overlap
 * nor spill out. The editor is deliberately the tallest body region (the editor dominates the screen). All
 * coordinates are relative to the window's top-left corner; the screen adds {@code leftPos}/{@code topPos}
 * at draw time and the screen draws every region from these same constants.
 */
public final class NmsLayout {

    public static final int WIDTH = 356;
    public static final int HEIGHT = 208;

    /** Small text scale used across the dense parts (tree, grid, status). */
    public static final float SMALL = 0.75f;

    // Top strips, stacked. The DesktopWindow draws the program's title bar, so the app has none of its own:
    // the menu bar is the top strip (TITLE_H = 0).
    public static final int TITLE_Y = 0;
    public static final int TITLE_H = 0;
    public static final int MENU_Y = TITLE_Y + TITLE_H;   // 0
    public static final int MENU_H = 10;
    public static final int TOOLBAR_Y = MENU_Y + MENU_H;  // 22
    public static final int TOOLBAR_H = 14;

    // Status bar, anchored to the bottom; the body fills the gap between the toolbar and it.
    public static final int STATUS_H = 16;
    public static final int STATUS_Y = HEIGHT - STATUS_H; // 204
    public static final int BODY_Y = TOOLBAR_Y + TOOLBAR_H; // 36
    public static final int BODY_H = STATUS_Y - BODY_Y;     // 168

    // Object Explorer column (left), then the vertical splitter, then the right pane.
    public static final int EXPLORER_X = 0;
    public static final int EXPLORER_W = 108;
    public static final int EX_HEAD_H = 10;
    public static final int EX_TREE_Y = BODY_Y + EX_HEAD_H; // 46
    public static final int EX_TREE_H = BODY_H - EX_HEAD_H;  // 158

    public static final int VSPLIT_X = EXPLORER_X + EXPLORER_W; // 108
    public static final int VSPLIT_W = 3;

    public static final int RIGHT_X = VSPLIT_X + VSPLIT_W;  // 111
    public static final int RIGHT_W = WIDTH - RIGHT_X;      // 245

    // Right pane, stacked: query tabs, the dominant editor, the horizontal splitter, results tabs, grid.
    public static final int TABS_H = 10;
    public static final int EDITOR_Y = BODY_Y + TABS_H;     // 46
    public static final int EDITOR_H = 100;                 // dominates the body (60% of 168)
    public static final int HSPLIT_Y = EDITOR_Y + EDITOR_H; // 146
    public static final int HSPLIT_H = 6;
    public static final int RES_TABS_Y = HSPLIT_Y + HSPLIT_H; // 152
    public static final int RES_TABS_H = 10;
    public static final int GRID_Y = RES_TABS_Y + RES_TABS_H;  // 162
    public static final int GRID_H = STATUS_Y - GRID_Y;        // 42

    /** Grid row pitch at the small scale; the results grid lists this many visible rows by default. */
    public static final int ROW_H = 9;

    // -----------------------------------------------------------------------
    // File menu dropdown — appears below the menu bar when File is clicked.
    // -----------------------------------------------------------------------

    /** Number of items in the File dropdown (New / Save / Save As... / Open...). */
    public static final int FILE_DROP_ITEMS = 4;

    /** Width of the File dropdown panel. */
    public static final int FILE_DROP_W = 76;

    /** Height of each item row inside the File dropdown. */
    public static final int FILE_DROP_ITEM_H = 10;

    /** X of the dropdown panel (left-aligned with the "File" label). */
    public static final int FILE_DROP_X = 6;

    /** Y of the top edge of the dropdown (immediately below the menu bar). */
    public static final int FILE_DROP_Y = MENU_Y + MENU_H;

    /** Total height of the File dropdown panel (border + rows). */
    public static final int FILE_DROP_H = FILE_DROP_ITEMS * FILE_DROP_ITEM_H + 2;

    // -----------------------------------------------------------------------
    // Modal dialogs — centred in the window.
    // -----------------------------------------------------------------------

    /** Width shared by both the Save-As and the Open picker dialogs. */
    public static final int DIALOG_W = 160;

    /** Height of the Save-As name-entry dialog. */
    public static final int DIALOG_H_SAVE = 38;

    /** Height of the Open file picker dialog. */
    public static final int DIALOG_H_OPEN = 90;

    /** X of the dialog panel (centred: (WIDTH - DIALOG_W) / 2). */
    public static final int DIALOG_X = (WIDTH - DIALOG_W) / 2;

    /** Y of the Save-As dialog (centred vertically). */
    public static final int DIALOG_SAVE_Y = (HEIGHT - DIALOG_H_SAVE) / 2;

    /** Y of the Open picker dialog (centred vertically). */
    public static final int DIALOG_OPEN_Y = (HEIGHT - DIALOG_H_OPEN) / 2;

    // Save-As dialog internals.

    /** X of the EditBox inside the Save-As dialog (window-local: imageWidth/2 - 50). */
    public static final int SAVE_EDIT_X = WIDTH / 2 - 50;

    /** Y of the EditBox inside the Save-As dialog (window-local: imageHeight/2 - 3). */
    public static final int SAVE_EDIT_Y = HEIGHT / 2 - 3;

    /** Width of the Save-As EditBox. */
    public static final int SAVE_EDIT_W = 100;

    /** Height of the Save-As EditBox. */
    public static final int SAVE_EDIT_H = 10;

    // Open picker dialog internals.

    /** Height of each row in the Open file picker. */
    public static final int PICKER_ROW_H = 10;

    /** Number of visible rows in the Open file picker. */
    public static final int PICKER_VISIBLE = 6;

    /** Height of the dialog header rail (title strip). */
    public static final int DIALOG_HEADER_H = 11;

    /** Y of the first picker row relative to the dialog top-left. */
    public static final int PICKER_LIST_OFFSET_Y = 12;

    /** Width of the Cancel button in the Open picker. */
    public static final int CANCEL_BTN_W = 38;

    /** Height of the Cancel button. */
    public static final int CANCEL_BTN_H = 10;

    /** X of the Cancel button relative to the dialog left edge. */
    public static final int CANCEL_BTN_REL_X = DIALOG_W - 42;

    /** Y of the Cancel button relative to the dialog top edge. */
    public static final int CANCEL_BTN_REL_Y = DIALOG_H_OPEN - 12;

    private NmsLayout() {
    }

    /**
     * The full region layout, ready for the overlap/out-of-bounds validators. The regions tile the window
     * with no gaps and no overlaps; the longest labels are added as text so a caption that runs off the
     * window is caught here, before the game.
     */
    public static GuiLayout layout() {
        final GuiLayout l = new GuiLayout(WIDTH, HEIGHT)
                .box("menuBar", 0, MENU_Y, WIDTH, MENU_H)
                .box("toolbar", 0, TOOLBAR_Y, WIDTH, TOOLBAR_H)
                .box("explorer", EXPLORER_X, BODY_Y, EXPLORER_W, BODY_H)
                .box("vSplit", VSPLIT_X, BODY_Y, VSPLIT_W, BODY_H)
                .box("queryTabs", RIGHT_X, BODY_Y, RIGHT_W, TABS_H)
                .box("editor", RIGHT_X, EDITOR_Y, RIGHT_W, EDITOR_H)
                .box("hSplit", RIGHT_X, HSPLIT_Y, RIGHT_W, HSPLIT_H)
                .box("resultsTabs", RIGHT_X, RES_TABS_Y, RIGHT_W, RES_TABS_H)
                .box("grid", RIGHT_X, GRID_Y, RIGHT_W, GRID_H)
                .box("statusBar", 0, STATUS_Y, WIDTH, STATUS_H);
        // The longest captions, at the scale the screen draws them, so gross overflow is caught.
        l.text("explorerHead", 6, BODY_Y + 2, 15, SMALL);          // "OBJECT EXPLORER"
        l.text("statusMsg", 6, STATUS_Y + 5, 27, SMALL);           // "Query executed successfully"
        l.text("gridHeadItem", RIGHT_X + 4, GRID_Y + 1, 4, SMALL); // "item"
        l.text("gridHeadServer", RIGHT_X + RIGHT_W / 2, GRID_Y + 1, 6, SMALL); // "server"
        return l;
    }

    /**
     * Layout snapshot for the state when the File dropdown is open: the menu bar plus the outer dropdown
     * panel and its item rows. The rows are non-overlapping leaf elements; the outer panel is verified for
     * bounds only (registered as a single solid so it cannot spill past the window). The dropdown must not
     * touch the toolbar below it and must not extend past the right or bottom window edge.
     *
     * <p>Only the outer dropdown box and the menu bar are registered as solid elements — they must not
     * overlap each other. The item rows are verified as text elements so their labels are bounds-checked
     * without triggering a false "overlap with the outer panel" failure.
     */
    public static GuiLayout dropdownOpen() {
        final GuiLayout l = new GuiLayout(WIDTH, HEIGHT)
                .box("menuBar", 0, MENU_Y, WIDTH, MENU_H)
                .box("fileDropdown", FILE_DROP_X, FILE_DROP_Y, FILE_DROP_W, FILE_DROP_H);
        // Item rows as text elements: longest label is "Save As..." = 10 chars.
        final String[] labels = {"New", "Save", "Save As...", "Open..."};
        for (int i = 0; i < labels.length; i++) {
            l.text("fileDropLabel_" + i,
                    FILE_DROP_X + 4,
                    FILE_DROP_Y + 1 + i * FILE_DROP_ITEM_H + 1,
                    labels[i].length(), SMALL);
        }
        return l;
    }

    /**
     * Layout snapshot for the Save-As dialog state: the interactive EditBox widget (the only solid element
     * inside the dialog) plus the text labels. The EditBox must sit entirely within the window. The outer
     * dialog panel is represented by its corner coordinates derived from {@link #DIALOG_X} /
     * {@link #DIALOG_SAVE_Y} — the labels being in-bounds is a sufficient proxy for the panel being in-bounds,
     * since they sit inside it.
     *
     * <p>The dialog outer panel is not registered as a second solid because it would nest (and therefore
     * overlap in the GuiLayout sense) with the EditBox — only non-overlapping leaf elements are solid here.
     */
    public static GuiLayout saveDialogOpen() {
        final GuiLayout l = new GuiLayout(WIDTH, HEIGHT)
                .box("saveEditBox", SAVE_EDIT_X, SAVE_EDIT_Y, SAVE_EDIT_W, SAVE_EDIT_H);
        // Text labels: bounds-checked against the window so any overflow spilling out is caught.
        l.text("saveAsTitle", DIALOG_X + 4, DIALOG_SAVE_Y + 2, 7, SMALL);      // "Save As"
        l.text("saveAsLabel", DIALOG_X + 4, DIALOG_SAVE_Y + 14, 10, SMALL);     // "File name:"
        // Longest hint: "Enter = save   Esc = cancel" = 28 chars.
        l.text("saveAsHint", DIALOG_X + 4, DIALOG_SAVE_Y + DIALOG_H_SAVE - 10, 28, SMALL);
        return l;
    }

    /**
     * Layout snapshot for the Open file picker dialog state: all visible picker rows (worst case:
     * {@link #PICKER_VISIBLE} rows) and the Cancel button as solid elements, plus header text. The rows
     * and Cancel button are non-overlapping leaves separated by vertical position; everything must sit
     * within the window.
     *
     * <p>The outer dialog panel is not registered as a second solid because it would nest (and therefore
     * overlap in the GuiLayout sense) with the rows and button — only the leaf widgets are solid here.
     */
    public static GuiLayout openPickerOpen() {
        final GuiLayout l = new GuiLayout(WIDTH, HEIGHT);
        // Picker rows: each row is a non-overlapping solid (sequential by y position).
        for (int i = 0; i < PICKER_VISIBLE; i++) {
            l.box("pickerRow_" + i,
                    DIALOG_X + 1,
                    DIALOG_OPEN_Y + PICKER_LIST_OFFSET_Y + i * PICKER_ROW_H,
                    DIALOG_W - 2,
                    PICKER_ROW_H);
        }
        // Cancel button: sits below the last picker row — verify it does not overlap any row.
        l.box("cancelBtn",
                DIALOG_X + CANCEL_BTN_REL_X,
                DIALOG_OPEN_Y + CANCEL_BTN_REL_Y,
                CANCEL_BTN_W,
                CANCEL_BTN_H);
        // Text labels: bounds-checked only.
        l.text("openTitle", DIALOG_X + 4, DIALOG_OPEN_Y + 2, 13, SMALL);  // "Open IQL File"
        l.text("openCancel", DIALOG_X + CANCEL_BTN_REL_X,
                DIALOG_OPEN_Y + CANCEL_BTN_REL_Y, 6, SMALL); // "Cancel"
        // Worst-case picker entry label: (DIALOG_W - 8) px / (GLYPH_WIDTH * SMALL) ≈ 33 glyphs.
        l.text("pickerEntry", DIALOG_X + 4, DIALOG_OPEN_Y + PICKER_LIST_OFFSET_Y + 2, 33, SMALL);
        return l;
    }
}
