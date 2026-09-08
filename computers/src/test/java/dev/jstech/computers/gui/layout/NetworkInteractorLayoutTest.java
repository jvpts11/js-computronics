/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

class NetworkInteractorLayoutTest {

    private static final int MIN_W = NetworkInteractorLayout.minContentWidth();
    private static final int MIN_H = NetworkInteractorLayout.minContentHeight();

    @Test
    void layout_isCleanAtMinimumSize() {
        final GuiLayout layout = NetworkInteractorLayout.toGuiLayout(MIN_W, MIN_H);
        assertTrue(layout.isClean(),
                "overlaps=" + layout.overlaps() + " outOfBounds=" + layout.outOfBounds());
    }

    @Test
    void layout_isCleanWhenStretchedWide() {
        final GuiLayout layout = NetworkInteractorLayout.toGuiLayout(520, MIN_H);
        assertTrue(layout.isClean(),
                "overlaps=" + layout.overlaps() + " outOfBounds=" + layout.outOfBounds());
    }

    @Test
    void layout_isCleanWhenTall() {
        final GuiLayout layout = NetworkInteractorLayout.toGuiLayout(MIN_W, 360);
        assertTrue(layout.isClean(),
                "overlaps=" + layout.overlaps() + " outOfBounds=" + layout.outOfBounds());
    }

    @Test
    void layout_isCleanAtTheDefaultSize() {
        // The app opens wide enough for the left column plus the details panel; content 320x214.
        final GuiLayout layout = NetworkInteractorLayout.toGuiLayout(320, 214);
        assertTrue(layout.isClean(),
                "overlaps=" + layout.overlaps() + " outOfBounds=" + layout.outOfBounds());
    }

    @Test
    void layout_isCleanAcrossEveryWindowSizeAtOrAboveTheMinimum() {
        /*
         * Sweep every size the window manager actually allows (it clamps to the per-app minimum). The four
         * bands must never overlap and nothing may spill past the panel, which is the bug the player hit (the
         * inventory rode up over the grid / fell off the window on resize). The framed inventory is always
         * fully visible because the minimum reserves room for the whole band.
         */
        for (int h = MIN_H; h <= 420; h += 5) {
            for (int w = MIN_W; w <= 540; w += 17) {
                final GuiLayout layout = NetworkInteractorLayout.toGuiLayout(w, h);
                assertTrue(layout.isClean(),
                        "not clean at " + w + "x" + h
                                + " overlaps=" + layout.overlaps() + " outOfBounds=" + layout.outOfBounds());
            }
        }
    }

    @Test
    void resolve_searchAndSortShareTheHeaderRowWithoutOverlapping() {
        /*
         * The sort button is clicked at these coordinates, not at "the right edge of the content": measuring
         * it against the whole width put its hit box out over the details panel, and the button did nothing.
         */
        for (int w = MIN_W; w <= 520; w += 37) {
            final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(w, MIN_H);
            assertTrue(z.sortW() > 0, "the sort button needs room at width " + w);
            assertTrue(z.searchX() + z.searchW() <= z.sortX(),
                    "search runs into the sort button at width " + w);
            assertTrue(z.sortX() + z.sortW() <= NetworkInteractorLayout.INSET + NetworkInteractorLayout.LEFT_W,
                    "the sort button must stay in the left column at width " + w);
            assertEquals(z.searchY(), z.sortY(), "both sit on the same header row at width " + w);
        }
    }

    @Test
    void resolve_inventoryBandIsAlwaysFullyVisibleAtTheMinimum() {
        // At the minimum height the inventory band still sits entirely inside the content, framed and whole.
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(MIN_W, MIN_H);
        assertTrue(z.invBandY() >= NetworkInteractorLayout.HEADER_H,
                "invBandY=" + z.invBandY() + " headerBottom=" + NetworkInteractorLayout.HEADER_H);
        assertTrue(z.invBandY() + z.invBandH() <= MIN_H - NetworkInteractorLayout.FOOTER_H,
                "band bottom=" + (z.invBandY() + z.invBandH()) + " footerTop=" + (MIN_H - NetworkInteractorLayout.FOOTER_H));
    }

    @Test
    void resolve_inventoryBandStaysFixedHeightWhenTheWindowGrows() {
        // The inventory band is a fixed-height panel: only the grid above it grows when the window is taller.
        final NetworkInteractorLayout.Zones small = NetworkInteractorLayout.resolve(300, MIN_H);
        final NetworkInteractorLayout.Zones tall = NetworkInteractorLayout.resolve(300, MIN_H + 180);
        assertEquals(NetworkInteractorLayout.INV_BAND_H, small.invBandH());
        assertEquals(NetworkInteractorLayout.INV_BAND_H, tall.invBandH());
    }

    @Test
    void resolve_inventoryBandSitsImmediatelyAboveTheFooter() {
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(300, 260);
        assertEquals(z.statusY(), z.invBandY() + z.invBandH(),
                "band bottom=" + (z.invBandY() + z.invBandH()) + " footerTop=" + z.statusY());
    }

    @Test
    void resolve_gridSitsBetweenTheHeaderAndTheInventoryBand() {
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(300, 280);
        assertEquals(NetworkInteractorLayout.HEADER_H, z.gridY());
        assertTrue(z.gridY() + z.gridH() <= z.invBandY(),
                "grid bottom=" + (z.gridY() + z.gridH()) + " invBandY=" + z.invBandY());
    }

    @Test
    void resolve_detailsPanelNeverOverflowsTheWindowAtAnySize() {
        /*
         * The bug that cut "WEIGHT"/"STORED": the panel must NEVER extend past the content box, at ANY size,
         * even far below the minimum (a window restored at a stale small size). It shrinks; it never spills.
         */
        for (int w = 100; w <= 560; w += 7) {
            for (int h = 100; h <= 440; h += 13) {
                final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(w, h);
                assertTrue(z.detailsX() + z.detailsW() <= w,
                        "details overflow at " + w + "x" + h + ": right="
                                + (z.detailsX() + z.detailsW()) + " w=" + w);
                assertTrue(z.detailsY() + z.detailsH() <= h, "details vertical overflow at " + w + "x" + h);
            }
        }
    }

    @Test
    void resolve_gridNeverOverlapsTheDetailsPanel() {
        /*
         * The grid lives in the left column; its right edge must never cross into the details panel. Checked
         * across the real allowed range (the window manager clamps to minWidth).
         */
        for (int w = MIN_W; w <= 560; w += 7) {
            final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(w, 260);
            final int gridRight = z.gridX() + z.gridCols() * NetworkInteractorLayout.CELL;
            assertTrue(gridRight <= z.detailsX(),
                    "grid right=" + gridRight + " detailsX=" + z.detailsX() + " at w=" + w);
        }
    }

    @Test
    void resolve_inventoryCellsTileExactlyAndStayInsideTheFrame() {
        /*
         * The 36 slots must tile edge-to-edge: 9 columns at col*CELL, rows contiguous except the single hotbar
         * gap before row 3, every cell fully inside the framed band, the seating that was wrong in-game.
         */
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(348, 252);
        final int cell = NetworkInteractorLayout.CELL;
        assertTrue(z.invX() >= z.invBandX() + NetworkInteractorLayout.INV_PAD, "slots start left of the frame");
        assertTrue(z.invX() + NetworkInteractorLayout.INV_COLS * cell
                        <= z.invBandX() + z.invBandW() - NetworkInteractorLayout.INV_PAD,
                "slots run right of the frame");
        for (int r = 0; r < NetworkInteractorLayout.INV_ROWS; r++) {
            final int expected = r * cell + (r >= 3 ? NetworkInteractorLayout.HOTBAR_GAP : 0);
            assertEquals(expected, NetworkInteractorLayout.rowYOffset(r), "row " + r + " y offset");
            final int rowY = z.invY() + NetworkInteractorLayout.rowYOffset(r);
            assertTrue(rowY >= z.invBandY() + NetworkInteractorLayout.INV_PAD, "row " + r + " above the frame");
            assertTrue(rowY + cell <= z.invBandY() + z.invBandH() - NetworkInteractorLayout.INV_PAD,
                    "row " + r + " below the frame");
        }
    }

    @Test
    void resolve_nothingEverSpillsPastTheContentBoxAtOrAboveTheMinimum() {
        /*
         * The strict version of the old generous sweep: at EVERY size the window manager actually allows (it
         * clamps to minWidth/minHeight), NO zone may fall outside the content box, checked every 7px.
         */
        for (int w = MIN_W; w <= 560; w += 7) {
            for (int h = MIN_H; h <= 440; h += 7) {
                final GuiLayout layout = NetworkInteractorLayout.toGuiLayout(w, h);
                assertTrue(layout.outOfBounds().isEmpty(),
                        "spills at " + w + "x" + h + ": " + layout.outOfBounds());
            }
        }
    }

    @Test
    void inventorySlotAt_roundTripsEverySlotCenterAndRejectsGapAndOutside() {
        /*
         * The hover/click hit-test (inventorySlotAt) must be the exact inverse of where the slots are drawn:
         * every drawn slot center maps back to its own index, so the hover always lands on the right slot.
         */
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(348, 252);
        final int cell = NetworkInteractorLayout.CELL;
        for (int r = 0; r < NetworkInteractorLayout.INV_ROWS; r++) {
            for (int c = 0; c < NetworkInteractorLayout.INV_COLS; c++) {
                final int cx = z.invX() + c * cell + cell / 2;
                final int cy = z.invY() + NetworkInteractorLayout.rowYOffset(r) + cell / 2;
                assertEquals(r * NetworkInteractorLayout.INV_COLS + c,
                        NetworkInteractorLayout.inventorySlotAt(cx, cy, z), "slot r" + r + " c" + c);
            }
        }
        // The hotbar gap, the columns' sides, and above the first row are NOT slots.
        assertEquals(-1, NetworkInteractorLayout.inventorySlotAt(z.invX() + cell / 2, z.invY() + 3 * cell + 1, z),
                "hotbar gap is not a slot");
        assertEquals(-1, NetworkInteractorLayout.inventorySlotAt(z.invX() - 1, z.invY() + cell / 2, z), "left of slots");
        assertEquals(-1, NetworkInteractorLayout.inventorySlotAt(
                z.invX() + NetworkInteractorLayout.INV_COLS * cell, z.invY() + cell / 2, z), "right of slots");
        assertEquals(-1, NetworkInteractorLayout.inventorySlotAt(z.invX() + cell / 2, z.invY() - 1, z), "above slots");
    }

    @Test
    void gridIndexAt_roundTripsVisibleCellsAndRejectsScrollbarAndOutside() {
        /*
         * The grid hover/click hit-test maps each drawn cell center back to its item index, honours the item
         * scroll, and rejects the thin scrollbar margin on the right (which previously fired spurious actions).
         */
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(348, 300);
        final int cell = NetworkInteractorLayout.CELL;
        for (int r = 0; r < z.gridRows(); r++) {
            for (int c = 0; c < z.gridCols(); c++) {
                final int cx = z.gridX() + c * cell + cell / 2;
                final int cy = z.gridY() + r * cell + cell / 2;
                assertEquals(r * z.gridCols() + c, NetworkInteractorLayout.gridIndexAt(cx, cy, 0, z),
                        "grid r" + r + " c" + c);
            }
        }
        assertEquals(2 * z.gridCols(), NetworkInteractorLayout.gridIndexAt(
                z.gridX() + cell / 2, z.gridY() + cell / 2, 2, z), "scroll offsets by whole rows");
        assertEquals(-1, NetworkInteractorLayout.gridIndexAt(
                z.gridX() + z.gridCols() * cell, z.gridY() + cell / 2, 0, z), "scrollbar strip is not a cell");
    }

    @Test
    void rowYOffset_insertsTheHotbarGapBeforeRow3() {
        /*
         * The real inventory slots (DesktopMenu.layoutInventory) and the drawn backgrounds (renderInventoryBand)
         * BOTH derive their row Y from this single function. The bug was layoutInventory using a plain row*18
         * that dropped the hotbar gap, so the hotbar items sat 4px off their slots. Lock the offsets here.
         */
        assertEquals(0, NetworkInteractorLayout.rowYOffset(0));
        assertEquals(NetworkInteractorLayout.CELL, NetworkInteractorLayout.rowYOffset(1));
        assertEquals(2 * NetworkInteractorLayout.CELL, NetworkInteractorLayout.rowYOffset(2));
        assertEquals(3 * NetworkInteractorLayout.CELL + NetworkInteractorLayout.HOTBAR_GAP,
                NetworkInteractorLayout.rowYOffset(3), "the hotbar row includes the gap");
    }

    @Test
    void resolve_gridShrinksToZeroRowsWhenNoSpace() {
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(300, MIN_H);
        assertEquals(0, z.gridRows());
    }

    @Test
    void resolve_gridGetsMoreRowsAsTheWindowGrows() {
        final int shortRows = NetworkInteractorLayout.resolve(300, MIN_H + 20).gridRows();
        final int tallRows = NetworkInteractorLayout.resolve(300, MIN_H + 200).gridRows();
        assertTrue(tallRows > shortRows, "shortRows=" + shortRows + " tallRows=" + tallRows);
    }

    @Test
    void resolve_slotsSitInsideTheFramePadding() {
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(300, 280);
        assertEquals(z.invBandX() + NetworkInteractorLayout.INV_PAD, z.invX());
        assertEquals(z.invBandY() + NetworkInteractorLayout.INV_PAD, z.invY());
    }
}
