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
 * Pure, Minecraft-free layout for the Network Interactor window, so both the app (which draws the grid, the
 * inventory frame, and the details panel, and runs the hit-tests) and the desktop screen (which positions the
 * player's real inventory slots) read the SAME zones from one place. Sharing the layout is what keeps the drawn
 * cells, the real container slots, and the click/hover hit-tests aligned at every window size.
 *
 * <p>All coordinates are content-local (origin at the window's content top-left, past the border and title
 * bar). The window has a fixed-width LEFT column and a flexible-width DETAILS column to its right:
 * <ul>
 *   <li>the left column holds the search/sort header, the GRID (which fills the height between the header and
 *       the inventory band and SCROLLS its items when there are more than fit), and a fixed, framed INVENTORY
 *       band pinned just above the footer (a {@value #INV_PAD}px border around the 36 slots, always visible);</li>
 *   <li>the details column fills the space to the right of the left column, from the header down to the footer,
 *       showing the hovered item's details (it is the panel that used to be dead space);</li>
 *   <li>the status bar and console line are pinned full-width at the bottom.</li>
 * </ul>
 *
 * <p>The inventory does NOT scroll: it is pinned with a constant height. Only the grid scrolls, and it scrolls
 * its ITEMS, not its pixels. Making the window TALLER grows the grid zone, showing more item rows. The natural
 * minimum keeps the header, the whole inventory band, the details panel, and the footer on screen at once.
 */
public final class NetworkInteractorLayout {

    public static final int TAB_H = 13;
    public static final int SEARCH_H = 13;
    public static final int STATUS_H = 11;
    public static final int CONSOLE_H = 10;
    public static final int CELL = 18;
    public static final int INV_COLS = 9;
    public static final int INV_ROWS = 4;          // 3 main rows + the hotbar, like the vanilla layout
    public static final int INV_H = INV_ROWS * CELL;
    public static final int INSET = 4;             // left/right content inset
    public static final int SORT_W = 46;           // width of the sort toggle box on a grid tab
    /** Padding of the framed inventory band around the 36 slots (vanilla-style border, sharp corners). */
    public static final int INV_PAD = 4;
    /** Gap between the left column and the details panel. */
    public static final int GAP = 5;
    /** Minimum width of the right-hand item details panel. */
    public static final int DETAILS_MIN_W = 122;
    /** The left column width: the framed inventory band (2*INV_PAD + 9 slots) sets it; the grid aligns inside. */
    public static final int LEFT_W = 2 * INV_PAD + INV_COLS * CELL;
    /** The first content row below the tab strip (where the search/sort header sits). */
    public static final int BODY_TOP = TAB_H + 2;

    /** Fixed header height: tab strip + search/sort row. The grid zone starts here. */
    public static final int HEADER_H = BODY_TOP + SEARCH_H + 3;
    /** Fixed footer height: the status bar plus the console line, pinned to the bottom. */
    public static final int FOOTER_H = STATUS_H + CONSOLE_H;
    /** Gap between the 3 main inventory rows and the hotbar row, like the vanilla inventory. */
    public static final int HOTBAR_GAP = 4;
    /** Fixed inventory-band height: the frame on each side, the 4 slot rows, and the hotbar gap. */
    public static final int INV_BAND_H = 2 * INV_PAD + INV_H + HOTBAR_GAP;

    /**
     * The y offset (from the top slot row) of inventory row {@code r}, inserting the hotbar gap before the
     * 4th row. The SINGLE source both the drawn slot backgrounds and the real container slots use, so they
     * always line up and the inventory reads as the vanilla 3-rows + gap + hotbar block.
     */
    public static int rowYOffset(final int r) {
        return r * CELL + (r >= 3 ? HOTBAR_GAP : 0);
    }

    /**
     * The inventory slot index (0..35, row-major: rows 0-2 are the 27 main slots, row 3 is the 9 hotbar slots)
     * under a content-local point, or -1 when the point is not on a slot — outside the columns, above/below the
     * rows, or in the hotbar gap. This is the EXACT inverse of where {@link #rowYOffset} places the slots, so
     * the hover/click hit-test always lands on the drawn cell and can never drift from the rendered position.
     */
    public static int inventorySlotAt(final int lx, final int ly, final Zones z) {
        final int relX = lx - z.invX();
        if (relX < 0 || relX >= INV_COLS * CELL) {
            return -1;
        }
        final int col = relX / CELL;
        for (int r = 0; r < INV_ROWS; r++) {
            final int rowTop = z.invY() + rowYOffset(r);
            if (ly >= rowTop && ly < rowTop + CELL) {
                return r * INV_COLS + col;
            }
        }
        return -1;
    }

    /**
     * The grid item index under a content-local point for the current item scroll, or -1 when off the grid
     * (outside the drawn cell columns/rows, including the thin scrollbar strip on the right). Same single
     * source the grid render uses, so a hovered cell is exactly the one drawn there.
     */
    public static int gridIndexAt(final int lx, final int ly, final int gridScroll, final Zones z) {
        if (z.gridRows() <= 0 || ly < z.gridY() || ly >= z.gridY() + z.gridH()) {
            return -1;
        }
        if (lx < z.gridX() || lx >= z.gridX() + z.gridCols() * CELL) {
            return -1;
        }
        final int col = (lx - z.gridX()) / CELL;
        final int row = (ly - z.gridY()) / CELL;
        if (col < 0 || col >= z.gridCols() || row < 0 || row >= z.gridRows()) {
            return -1;
        }
        return (gridScroll + row) * z.gridCols() + col;
    }

    private NetworkInteractorLayout() {
    }

    /**
     * Resolved content-local zones for one window size. The grid is the only scrolling region (its items
     * scroll, not its pixels); the inventory band, the details panel, and the footer are pinned.
     * {@code gridRows}/{@code gridCols} are how many item rows/columns currently fit in the grid zone.
     */
    public record Zones(int searchX, int searchY, int searchW,
                        int sortX, int sortY, int sortW,
                        int gridX, int gridY, int gridW, int gridH, int gridCols, int gridRows,
                        int invBandX, int invBandY, int invBandW, int invBandH,
                        int invX, int invY,
                        int detailsX, int detailsY, int detailsW, int detailsH,
                        int statusY, int consoleY) {
    }

    /** The smallest content width: the left column, the gap, and the minimum details panel, plus the insets. */
    public static int minContentWidth() {
        return 2 * INSET + LEFT_W + GAP + DETAILS_MIN_W;
    }

    /**
     * The smallest content height: the fixed header, the whole framed inventory band, and the footer. At this
     * height the grid zone collapses to zero rows but the inventory stays fully visible inside its frame.
     */
    public static int minContentHeight() {
        return HEADER_H + INV_BAND_H + FOOTER_H;
    }

    /**
     * Computes every zone for the given content size. The left column is fixed-width (the grid above the framed
     * inventory band); the details panel fills the rest of the width from the header to the footer; the footer
     * is full-width at the bottom. When the window is too short the grid shrinks to zero rows (and scrolls its
     * items) while the inventory keeps its frame; making it taller grows the grid.
     */
    public static Zones resolve(final int contentW, final int contentH) {
        final int headerBottom = HEADER_H;
        final int footerTop = Math.max(headerBottom, contentH - FOOTER_H);
        // The inventory band sits just above the footer; it never climbs above the header.
        final int invBandY = Math.max(headerBottom, footerTop - INV_BAND_H);
        // The grid fills whatever is left between the header and the inventory band (zero when squashed).
        final int gridTop = headerBottom;
        final int gridAreaH = Math.max(0, invBandY - gridTop);

        // Header search/sort live within the left column only.
        final int searchX = INSET;
        final int searchY = BODY_TOP;
        final int searchW = Math.max(CELL, LEFT_W - SORT_W - 6);
        final int sortX = INSET + LEFT_W - SORT_W;
        final int sortW = SORT_W;

        // Grid aligns with the inventory slots (inset by the band's frame padding), 9 columns wide.
        final int gridX = INSET + INV_PAD;
        final int gridW = INV_COLS * CELL;
        final int gridCols = INV_COLS;
        final int gridRows = Math.max(0, gridAreaH / CELL);

        final int invBandX = INSET;
        final int invBandW = LEFT_W;
        final int invX = invBandX + INV_PAD;
        final int invY = invBandY + INV_PAD;

        // The details panel fills the width to the right of the left column, from the header to the footer.
        // NEVER force it wider than the room left, or it would overflow the window and be clipped by the
        // border (the bug that cut "WEIGHT"/"STORED"). The window manager keeps the whole window at or above
        // minContentWidth so the panel still has its minimum room in practice.
        final int detailsX = Math.min(INSET + LEFT_W + GAP, Math.max(0, contentW - INSET));
        final int detailsY = headerBottom;
        final int detailsW = Math.max(0, contentW - detailsX - INSET);
        final int detailsH = Math.max(0, footerTop - detailsY);

        final int statusY = footerTop;
        final int consoleY = footerTop + STATUS_H;

        return new Zones(searchX, searchY, searchW, sortX, searchY, sortW,
                gridX, gridTop, gridW, gridAreaH, gridCols, gridRows,
                invBandX, invBandY, invBandW, INV_BAND_H,
                invX, invY,
                detailsX, detailsY, detailsW, detailsH,
                statusY, consoleY);
    }

    /**
     * Builds a {@link GuiLayout} of the solid zones the player actually sees for one window size, so a unit
     * test can assert nothing overlaps and nothing spills past the content area: the grid, the framed inventory
     * band and its 36 slots, the details panel, the header fields, and the footer.
     */
    public static GuiLayout toGuiLayout(final int contentW, final int contentH) {
        final Zones z = resolve(contentW, contentH);
        final GuiLayout layout = new GuiLayout(contentW, contentH);
        layout.box("tabs", 0, 0, contentW, TAB_H);
        layout.box("search", z.searchX(), z.searchY(), z.searchW(), SEARCH_H);
        layout.box("sort", z.sortX(), z.sortY(), z.sortW(), SEARCH_H);

        // Grid: every row that fits in the grid zone (the grid scrolls its items, so its pixel box is fixed).
        if (z.gridRows() > 0) {
            layout.box("grid", z.gridX(), z.gridY(), z.gridCols() * CELL, z.gridRows() * CELL);
        }

        // The details panel to the right of the left column.
        if (z.detailsH() > 0) {
            layout.box("details", z.detailsX(), z.detailsY(), z.detailsW(), z.detailsH());
        }

        // The framed inventory band: four border strips around the 36 slots (the bevel), then the slots.
        final int slotsW = INV_COLS * CELL;
        final int slotsH = INV_ROWS * CELL + HOTBAR_GAP;
        layout.box("inv_frame_top", z.invBandX(), z.invBandY(), z.invBandW(), INV_PAD);
        layout.box("inv_frame_bottom", z.invBandX(), z.invBandY() + INV_PAD + slotsH, z.invBandW(), INV_PAD);
        layout.box("inv_frame_left", z.invBandX(), z.invBandY() + INV_PAD, INV_PAD, slotsH);
        layout.box("inv_frame_right", z.invBandX() + INV_PAD + slotsW, z.invBandY() + INV_PAD, INV_PAD, slotsH);
        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                layout.box("inv_" + r + "_" + c, z.invX() + c * CELL, z.invY() + rowYOffset(r), CELL, CELL);
            }
        }

        layout.box("status", 0, z.statusY(), contentW, STATUS_H);
        layout.box("console", 0, z.consoleY(), contentW, CONSOLE_H);
        return layout;
    }
}
