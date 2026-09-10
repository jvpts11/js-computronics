/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;

/**
 * Pure, Minecraft-free layout for the Network Interactor window, so both the app (which draws the grid, the
 * inventory frame, and the details panel, and runs the hit-tests) and the desktop screen (which positions the
 * player's real inventory slots) read the SAME zones from one place. Sharing the layout is what keeps the drawn
 * cells, the real container slots, and the click/hover hit-tests aligned at every window size.
 *
 * <p>All coordinates are content-local (origin at the window's content top-left, past the border and title
 * bar). The window has a LEFT column and a flexible-width DETAILS column to its right:
 * <ul>
 *   <li>the left column holds the toolbar band (search, the two filters and the sort), a caption strip, the
 *       GRID in a sunken well (it fills the height between the caption and the inventory band and SCROLLS its
 *       items when there are more than fit), a grip strip, and a framed INVENTORY well pinned just above the
 *       footer (a {@value #INV_PAD}px border around the slots);</li>
 *   <li>the details column fills the space to the right of the left column, from the caption strip down to
 *       the footer, showing the hovered item's details;</li>
 *   <li>the status bar and a keyboard hint line are pinned full-width at the bottom.</li>
 * </ul>
 *
 * <p>Two grips let the player reshape the window's insides, and the places they set are kept with the window:
 * the vertical grip between the left column and the details panel gives the grid whole columns beyond the
 * inventory's nine ({@code extraCols}; the band stays nine wide and sits centred under the wider grid), and the
 * horizontal grip above the inventory folds the band's top rows away ({@code invRows}, one to four, the hotbar
 * always staying) so the grid gets their room. The inventory does NOT scroll; only the grid scrolls, and it
 * scrolls its ITEMS, not its pixels.
 */
public final class NetworkInteractorLayout {

    public static final int TAB_H = 13;
    public static final int SEARCH_H = 13;
    public static final int STATUS_H = 11;
    /** The keyboard hint line under the status bar. */
    public static final int HINT_H = 8;
    /** The caption strip over the grid's well ("NETWORK STORAGE · 9 types"). */
    public static final int CAP_H = 8;
    public static final int CELL = 18;
    public static final int INV_COLS = 9;
    public static final int INV_ROWS = 4;          // 3 main rows + the hotbar, like the vanilla layout
    public static final int INV_H = INV_ROWS * CELL;
    public static final int INSET = 4;             // left/right content inset
    public static final int SORT_W = 32;           // width of the sort toggle box on a grid tab (small label)
    /** Width of the mod filter drop-down in the header row. */
    public static final int MOD_W = 34;
    /** Width of the category filter drop-down in the header row: "Category" at the small scale. */
    public static final int CAT_W = 46;
    /** Padding of the framed inventory band around the slots (vanilla-style border, sharp corners). */
    public static final int INV_PAD = 4;
    /** Gap between the left column and the details panel, where the vertical grip sits. */
    public static final int GAP = 5;
    /** The strip between the grid and the inventory band: the band's label and the horizontal grip. */
    public static final int GRIP_H = 9;
    /** Minimum width of the right-hand item details panel. */
    public static final int DETAILS_MIN_W = 122;
    /** The left column's base width: the framed inventory band (2*INV_PAD + 9 slots); the grid aligns inside. */
    public static final int LEFT_W = 2 * INV_PAD + INV_COLS * CELL;
    /** The first content row below the tab strip (where the search/sort header sits). */
    public static final int BODY_TOP = TAB_H + 2;

    /** Fixed header height: tab strip + the toolbar band. The caption strip and the details panel start here. */
    public static final int HEADER_H = BODY_TOP + SEARCH_H + 3;
    /** Fixed footer height: the status bar and the hint line, pinned to the bottom. */
    public static final int FOOTER_H = STATUS_H + HINT_H;
    /** Gap between the 3 main inventory rows and the hotbar row, like the vanilla inventory. */
    public static final int HOTBAR_GAP = 4;
    /** Fixed inventory-band height with every row shown: the frame on each side, the 4 slot rows, and the gap. */
    public static final int INV_BAND_H = 2 * INV_PAD + INV_H + HOTBAR_GAP;

    /**
     * The y offset (from the top slot row) of inventory row {@code r}, inserting the hotbar gap before the
     * 4th row. The SINGLE source both the drawn slot backgrounds and the real container slots use, so they
     * always line up and the inventory reads as the vanilla 3-rows + gap + hotbar block.
     */
    public static int rowYOffset(final int r) {
        return r * CELL + (r >= 3 ? HOTBAR_GAP : 0);
    }

    /** The band height when its {@code rows} bottom rows are shown (the hotbar and the main rows above it). */
    public static int bandHeight(final int rows) {
        final int shown = Math.max(1, Math.min(INV_ROWS, rows));
        return 2 * INV_PAD + shown * CELL + (shown >= 2 ? HOTBAR_GAP : 0);
    }

    /** The content-local top of slot row {@code r}, or a row above the band when {@code r} is folded away. */
    public static int slotRowY(final Zones z, final int r) {
        return z.invY() + rowYOffset(r) - rowYOffset(z.invFirstRow());
    }

    /**
     * The inventory slot index (0..35, row-major: rows 0-2 are the 27 main slots, row 3 is the 9 hotbar slots)
     * under a content-local point, or -1 when the point is not on a slot, outside the columns, above/below the
     * rows, in the hotbar gap, or on a row the band has folded away. This is the EXACT inverse of where
     * {@link #slotRowY} places the slots, so the hover/click hit-test always lands on the drawn cell.
     */
    public static int inventorySlotAt(final int lx, final int ly, final Zones z) {
        final int relX = lx - z.invX();
        if (relX < 0 || relX >= INV_COLS * CELL) {
            return -1;
        }
        final int col = relX / CELL;
        for (int r = z.invFirstRow(); r < INV_ROWS; r++) {
            final int rowTop = slotRowY(z, r);
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

    /** Whether a content-local point is on the vertical grip (the gap between the left column and the details). */
    public static boolean onVerticalGrip(final int lx, final int ly, final Zones z) {
        return lx >= z.gripX() && lx < z.gripX() + GAP && ly >= z.capY() && ly < z.invBandY() + z.invBandH();
    }

    /** Whether a content-local point is on the horizontal grip strip above the inventory band. */
    public static boolean onHorizontalGrip(final int lx, final int ly, final Zones z) {
        return ly >= z.gripY() && ly < z.gripY() + GRIP_H && lx >= z.invBandX() && lx < z.invBandX() + z.invBandW();
    }

    /** The most columns the grid can have beyond the inventory's nine at this content width. */
    public static int maxExtraCols(final int contentW) {
        return Math.max(0, (contentW - minContentWidth()) / CELL);
    }

    /** The extra columns a vertical-grip drag to content-local {@code lx} asks for, before clamping. */
    public static int extraColsForGrip(final int lx) {
        return Math.round((lx - (INSET + LEFT_W)) / (float) CELL);
    }

    /** The inventory rows a horizontal-grip drag to content-local {@code ly} asks for, before clamping. */
    public static int invRowsForGrip(final int ly, final int contentH) {
        final int footerTop = contentH - FOOTER_H;
        // The band's slots end INV_PAD above the footer; count whole rows between the grip and that edge.
        final int rowsBelow = Math.round((footerTop - INV_PAD - ly - INV_PAD - HOTBAR_GAP) / (float) CELL);
        return Math.max(1, Math.min(INV_ROWS, rowsBelow));
    }

    private NetworkInteractorLayout() {
    }

    /**
     * Resolved content-local zones for one window size. The grid is the only scrolling region (its items
     * scroll, not its pixels); the inventory band, the details panel, and the footer are pinned.
     * {@code gridRows}/{@code gridCols} are how many item rows/columns currently fit in the grid zone;
     * {@code invFirstRow} is the first inventory row the band shows (rows above it are folded away).
     */
    public record Zones(int searchX, int searchY, int searchW,
                        int modX, int modW, int catX, int catW,
                        int sortX, int sortY, int sortW,
                        int capY,
                        int wellX, int wellY, int wellW, int wellH,
                        int gridX, int gridY, int gridW, int gridH, int gridCols, int gridRows,
                        int gripX, int gripY,
                        int invBandX, int invBandY, int invBandW, int invBandH,
                        int invX, int invY, int invFirstRow,
                        int detailsX, int detailsY, int detailsW, int detailsH,
                        int statusY, int hintY) {
    }

    /** The smallest content width: the left column, the gap, and the minimum details panel, plus the insets. */
    public static int minContentWidth() {
        return 2 * INSET + LEFT_W + GAP + DETAILS_MIN_W;
    }

    /**
     * The smallest content height: the fixed header, the caption strip, an empty well, the grip strip, the
     * whole framed inventory band, and the footer. At this height the grid has zero rows but the inventory
     * stays fully visible.
     */
    public static int minContentHeight() {
        return HEADER_H + CAP_H + 2 * INV_PAD + GRIP_H + INV_BAND_H + FOOTER_H;
    }

    /** The zones with the grid at the inventory's nine columns and every inventory row shown. */
    public static Zones resolve(final int contentW, final int contentH) {
        return resolve(contentW, contentH, 0, INV_ROWS);
    }

    /**
     * Computes every zone for the given content size, with the grips at {@code extraCols} columns beyond the
     * inventory's nine (clamped to what leaves the details panel its minimum) and {@code invRows} inventory
     * rows shown (one to four). The left column holds the grid above the framed inventory band; the details
     * panel fills the rest of the width from the header to the footer; the footer is full-width at the bottom.
     * When the window is too short the grid shrinks to zero rows (and scrolls its items) while the inventory
     * keeps its frame; making it taller, or folding inventory rows away, grows the grid.
     */
    public static Zones resolve(final int contentW, final int contentH, final int extraCols, final int invRows) {
        final int cols = INV_COLS + Math.max(0, Math.min(extraCols, maxExtraCols(contentW)));
        final int leftW = 2 * INV_PAD + cols * CELL;
        int rows = Math.max(1, Math.min(INV_ROWS, invRows));
        // A window shorter than the whole band folds inventory rows away rather than letting the band ride the footer.
        while (rows > 1 && HEADER_H + CAP_H + 2 * INV_PAD + GRIP_H + bandHeight(rows) + FOOTER_H > contentH) {
            rows--;
        }
        final int bandH = bandHeight(rows);

        final int headerBottom = HEADER_H;
        final int footerTop = Math.max(headerBottom, contentH - FOOTER_H);
        // The caption strip sits under the toolbar; the grid's well starts under it.
        final int capY = headerBottom;
        final int wellY = capY + CAP_H;
        // The inventory band sits just above the footer; it never climbs above the well's frame.
        final int invBandY = Math.max(wellY + 2 * INV_PAD + GRIP_H, footerTop - bandH);
        // The grip strip sits right above the band; the well fills what is left between the caption and it.
        final int gripY = invBandY - GRIP_H;
        final int wellH = Math.max(2 * INV_PAD, gripY - 1 - wellY);
        final int gridTop = wellY + INV_PAD;
        final int gridAreaH = Math.max(0, wellH - 2 * INV_PAD);

        // Header search/filters/sort live within the left column only.
        final int searchX = INSET;
        final int searchY = BODY_TOP;
        final int sortX = INSET + leftW - SORT_W;
        final int catX = sortX - 2 - CAT_W;
        final int modX = catX - 2 - MOD_W;
        final int searchW = Math.max(CELL, modX - 2 - searchX);

        // The well spans the left column; the grid sits inside its frame padding, cols columns wide.
        final int wellX = INSET;
        final int wellW = leftW;
        final int gridX = wellX + INV_PAD;
        final int gridW = cols * CELL;
        final int gridRows = Math.max(0, gridAreaH / CELL);

        // The band keeps the inventory's nine columns and sits centred under a grid that grew wider than it.
        final int invBandX = INSET + (leftW - LEFT_W) / 2;
        final int invBandW = LEFT_W;
        final int invX = invBandX + INV_PAD;
        final int invY = invBandY + INV_PAD;
        final int invFirstRow = INV_ROWS - rows;

        /*
         * The details panel fills the width to the right of the left column, from the header to the footer.
         * NEVER force it wider than the room left, or it would overflow the window and be clipped by the
         * border (the bug that cut "WEIGHT"/"STORED"). The window manager keeps the whole window at or above
         * minContentWidth so the panel still has its minimum room in practice.
         */
        final int gripX = INSET + leftW;
        final int detailsX = Math.min(gripX + GAP, Math.max(0, contentW - INSET));
        final int detailsY = headerBottom;
        final int detailsW = Math.max(0, contentW - detailsX - INSET);
        final int detailsH = Math.max(0, footerTop - detailsY);

        final int statusY = footerTop;
        final int hintY = footerTop + STATUS_H;

        return new Zones(searchX, searchY, searchW,
                modX, MOD_W, catX, CAT_W,
                sortX, searchY, SORT_W,
                capY,
                wellX, wellY, wellW, wellH,
                gridX, gridTop, gridW, gridRows * CELL, cols, gridRows,
                gripX, gripY,
                invBandX, invBandY, invBandW, bandH,
                invX, invY, invFirstRow,
                detailsX, detailsY, detailsW, detailsH,
                statusY, hintY);
    }

    /** The solid zones with the grid at nine columns and every inventory row shown. */
    public static GuiLayout toGuiLayout(final int contentW, final int contentH) {
        return toGuiLayout(contentW, contentH, 0, INV_ROWS);
    }

    /**
     * Builds a {@link GuiLayout} of the solid zones the player actually sees for one window size and grip
     * setting, so a unit test can assert nothing overlaps and nothing spills past the content area: the
     * grid, the grip strip, the framed inventory band and its shown slots, the details panel, the header
     * fields, and the footer.
     */
    public static GuiLayout toGuiLayout(final int contentW, final int contentH, final int extraCols, final int invRows) {
        final Zones z = resolve(contentW, contentH, extraCols, invRows);
        final GuiLayout layout = new GuiLayout(contentW, contentH);
        layout.box("tabs", 0, 0, contentW, TAB_H);
        layout.box("search", z.searchX(), z.searchY(), z.searchW(), SEARCH_H);
        layout.box("mod", z.modX(), z.searchY(), z.modW(), SEARCH_H);
        layout.box("category", z.catX(), z.searchY(), z.catW(), SEARCH_H);
        layout.box("sort", z.sortX(), z.sortY(), z.sortW(), SEARCH_H);

        // The caption strip, then the well's four frame strips around the grid cells (the grid scrolls its
        // items, so its pixel box is fixed); whatever the frame leaves under the last row is the well's own.
        layout.box("caption", z.wellX(), z.capY(), z.wellW(), CAP_H);
        layout.box("well_top", z.wellX(), z.wellY(), z.wellW(), INV_PAD);
        layout.box("well_left", z.wellX(), z.wellY() + INV_PAD, INV_PAD, z.wellH() - 2 * INV_PAD);
        layout.box("well_right", z.wellX() + z.wellW() - INV_PAD, z.wellY() + INV_PAD, INV_PAD, z.wellH() - 2 * INV_PAD);
        layout.box("well_bottom", z.wellX(), z.wellY() + z.wellH() - INV_PAD, z.wellW(), INV_PAD);
        if (z.gridRows() > 0) {
            layout.box("grid", z.gridX(), z.gridY(), z.gridCols() * CELL, z.gridRows() * CELL);
        }
        layout.box("grip_h", z.invBandX(), z.gripY(), z.invBandW(), GRIP_H);
        layout.box("grip_v", z.gripX(), z.capY(), GAP, z.invBandY() + z.invBandH() - z.capY());

        // The details panel to the right of the left column.
        if (z.detailsH() > 0) {
            layout.box("details", z.detailsX(), z.detailsY(), z.detailsW(), z.detailsH());
        }

        // The framed inventory band: four border strips around the shown slots (the bevel), then the slots.
        final int slotsW = INV_COLS * CELL;
        final int slotsH = z.invBandH() - 2 * INV_PAD;
        layout.box("inv_frame_top", z.invBandX(), z.invBandY(), z.invBandW(), INV_PAD);
        layout.box("inv_frame_bottom", z.invBandX(), z.invBandY() + INV_PAD + slotsH, z.invBandW(), INV_PAD);
        layout.box("inv_frame_left", z.invBandX(), z.invBandY() + INV_PAD, INV_PAD, slotsH);
        layout.box("inv_frame_right", z.invBandX() + INV_PAD + slotsW, z.invBandY() + INV_PAD, INV_PAD, slotsH);
        for (int r = z.invFirstRow(); r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                layout.box("inv_" + r + "_" + c, z.invX() + c * CELL, slotRowY(z, r), CELL, CELL);
            }
        }

        layout.box("status", 0, z.statusY(), contentW, STATUS_H);
        layout.box("hint", 0, z.hintY(), contentW, HINT_H);
        return layout;
    }
}
