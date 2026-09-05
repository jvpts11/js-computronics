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
 * The geometry of the Files explorer window, in desktop units, kept out of the screen so the pieces
 * that must never overlap (the toolbar's buttons, the address, the search box, the drive tree, the
 * column header, the list and the status bar) are checked by a test instead of by a screenshot.
 *
 * <p>Top to bottom: a toolbar (back, forward, up, the address trail, a search box, the view toggle),
 * then the drive tree on the left beside a column header and the file list, then the status bar.
 */
public final class FilesLayout {

    public static final int DEFAULT_W = 300;
    public static final int DEFAULT_H = 190;
    public static final int MIN_W = 250;
    public static final int MIN_H = 120;

    /** The toolbar row. */
    public static final int TOOL_H = 14;
    /** One navigation button (back, forward, up, view), square-ish, and the gap between them. */
    public static final int NAV_W = 12;
    public static final int NAV_H = 11;
    public static final int NAV_GAP = 1;
    /** The search box on the toolbar's right. */
    public static final int SEARCH_W = 60;
    /** The drive tree: wide enough for "Local Disk (C:)" at the desktop font. */
    public static final int TREE_W = 84;
    /** The column header over the list. */
    public static final int COLS_H = 10;
    public static final int STATUS_H = 11;
    public static final int ROW_H = 11;
    public static final int ICON_W = 12;
    /** The Type and Size columns, right-aligned in the list. */
    public static final int TYPE_COL_W = 54;
    public static final int SIZE_COL_W = 40;
    /** The context menu. */
    public static final int CTX_W = 96;
    public static final int CTX_ITEM_H = 11;
    /** The properties panel drawn inside the list area. */
    public static final int PROPS_W = 150;
    public static final int PROPS_H = 74;

    private FilesLayout() {
    }

    /** The x of navigation button {@code i}: 0 back, 1 forward, 2 up. */
    public static int navX(final int i) {
        return 2 + i * (NAV_W + NAV_GAP);
    }

    public static int navY() {
        return (TOOL_H - NAV_H) / 2;
    }

    /** The view toggle sits at the right edge of the toolbar. */
    public static int viewX(final int width) {
        return width - 2 - NAV_W;
    }

    public static int searchX(final int width) {
        return viewX(width) - 2 - SEARCH_W;
    }

    public static int addressX() {
        return navX(3) + 1;
    }

    public static int addressW(final int width) {
        return Math.max(20, searchX(width) - 2 - addressX());
    }

    public static int treeY() {
        return TOOL_H + 1;
    }

    public static int treeH(final int height) {
        return height - treeY() - STATUS_H - 1;
    }

    public static int listX() {
        return TREE_W + 1;
    }

    public static int listW(final int width) {
        return width - listX();
    }

    public static int colsY() {
        return treeY();
    }

    public static int listY() {
        return colsY() + COLS_H;
    }

    public static int listH(final int height) {
        return height - listY() - STATUS_H - 1;
    }

    public static int statusY(final int height) {
        return height - STATUS_H;
    }

    /** How many rows the list shows at {@code height}. */
    public static int visibleRows(final int height) {
        return Math.max(1, (listH(height) - 2) / ROW_H);
    }

    /** The x where the Type column begins, measured from the window's left edge. */
    public static int typeColX(final int width) {
        return width - SIZE_COL_W - TYPE_COL_W;
    }

    public static int sizeColX(final int width) {
        return width - SIZE_COL_W;
    }

    /** The widest a name may be drawn before it runs into the Type column. */
    public static int nameMaxW(final int width) {
        return typeColX(width) - (listX() + 4 + ICON_W + 3) - 3;
    }

    /** The whole window as solid boxes, so a test proves nothing overlaps at a given size. */
    public static GuiLayout layout(final int width, final int height) {
        final GuiLayout l = new GuiLayout(width, height);
        for (int i = 0; i < 3; i++) {
            l.box("nav" + i, navX(i), navY(), NAV_W, NAV_H);
        }
        l.box("address", addressX(), navY(), addressW(width), NAV_H);
        l.box("search", searchX(width), navY(), SEARCH_W, NAV_H);
        l.box("view", viewX(width), navY(), NAV_W, NAV_H);
        l.box("tree", 0, treeY(), TREE_W, treeH(height));
        l.box("columns", listX(), colsY(), listW(width), COLS_H);
        l.box("list", listX(), listY(), listW(width), listH(height));
        l.box("status", 0, statusY(height), width, STATUS_H);
        return l;
    }

    /** One list row as its three columns, so a test proves the name never runs into the Type column. */
    public static GuiLayout columns(final int width) {
        final GuiLayout l = new GuiLayout(width, ROW_H);
        l.box("name-col", listX() + 4, 0, nameMaxW(width) + ICON_W + 3, ROW_H);
        l.box("type-col", typeColX(width), 0, TYPE_COL_W - 2, ROW_H);
        l.box("size-col", sizeColX(width), 0, SIZE_COL_W - 2, ROW_H);
        return l;
    }
}
