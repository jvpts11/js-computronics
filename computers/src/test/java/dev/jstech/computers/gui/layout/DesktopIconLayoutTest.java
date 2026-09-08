/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DesktopIconLayoutTest {

    @Test
    void packAndUnpack_roundTripColumnAndRow() {
        final int cell = DesktopIconLayout.pack(3, 5);
        assertEquals(3, DesktopIconLayout.col(cell));
        assertEquals(5, DesktopIconLayout.row(cell));
    }

    @Test
    void resolve_flowsUnpinnedIconsColumnMajor() {
        // Five icons, no pins, three rows per column: fills column 0 (rows 0,1,2), then column 1 (rows 0,1).
        final List<String> keys = List.of("a", "b", "c", "d", "e");
        final int[] cells = DesktopIconLayout.resolve(keys, Map.of(), 3);
        assertEquals(DesktopIconLayout.pack(0, 0), cells[0]);
        assertEquals(DesktopIconLayout.pack(0, 1), cells[1]);
        assertEquals(DesktopIconLayout.pack(0, 2), cells[2]);
        assertEquals(DesktopIconLayout.pack(1, 0), cells[3]);
        assertEquals(DesktopIconLayout.pack(1, 1), cells[4]);
    }

    @Test
    void resolve_keepsAPinnedIconOnItsStoredCell() {
        final Map<String, Integer> pinned = new HashMap<>();
        pinned.put("b", DesktopIconLayout.pack(4, 2));
        final int[] cells = DesktopIconLayout.resolve(List.of("a", "b", "c"), pinned, 5);
        assertEquals(DesktopIconLayout.pack(4, 2), cells[1], "the pinned icon must stay on its cell");
    }

    @Test
    void resolve_unpinnedIconsSkipAPinnedCell() {
        // 'a' is pinned to (0,0); the unpinned 'b','c' must NOT reuse that cell.
        final Map<String, Integer> pinned = new HashMap<>();
        pinned.put("a", DesktopIconLayout.pack(0, 0));
        final int[] cells = DesktopIconLayout.resolve(List.of("a", "b", "c"), pinned, 3);
        assertEquals(DesktopIconLayout.pack(0, 0), cells[0]);
        assertNotEquals(cells[0], cells[1]);
        assertNotEquals(cells[0], cells[2]);
        assertNotEquals(cells[1], cells[2]);
    }

    @Test
    void resolve_neverPlacesTwoIconsOnTheSameCell() {
        final Map<String, Integer> pinned = new HashMap<>();
        pinned.put("b", DesktopIconLayout.pack(0, 1));
        pinned.put("d", DesktopIconLayout.pack(1, 0));
        final int[] cells = DesktopIconLayout.resolve(List.of("a", "b", "c", "d", "e", "f"), pinned, 3);
        final Set<Integer> seen = new HashSet<>();
        for (final int cell : cells) {
            assertTrue(seen.add(cell), "no cell may be used twice; collided on " + cell);
        }
    }

    @Test
    void resolve_clampsAPinnedRowFromATallerMonitorIntoRange() {
        // A cell pinned at row 5 on a once-taller monitor must clamp to the last row (2) when only 3 rows fit.
        final Map<String, Integer> pinned = new HashMap<>();
        pinned.put("a", DesktopIconLayout.pack(0, 5));
        final int[] cells = DesktopIconLayout.resolve(List.of("a"), pinned, 3);
        assertEquals(2, DesktopIconLayout.row(cells[0]), "row must clamp into [0, perColumn)");
        assertEquals(0, DesktopIconLayout.col(cells[0]), "column must be preserved");
    }

    @Test
    void resolve_pullsANegativeColumnBackToZero() {
        final Map<String, Integer> pinned = new HashMap<>();
        pinned.put("a", DesktopIconLayout.pack(-2, 1));
        final int[] cells = DesktopIconLayout.resolve(List.of("a"), pinned, 4);
        assertFalse(DesktopIconLayout.col(cells[0]) < 0, "a negative column must be pulled back onto the grid");
    }

    @Test
    void resolve_returnsOneCellPerKeyInOrder() {
        final List<String> keys = List.of("a", "b", "c", "d");
        final int[] cells = DesktopIconLayout.resolve(keys, Map.of(), 2);
        assertEquals(keys.size(), cells.length);
    }
}
