/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.gui.layout;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure grid math for free-positioned desktop icons, with no Minecraft dependency so it can be unit-tested.
 *
 * <p>Every icon occupies one grid cell identified by a column and a row, packed into a single int
 * ({@code col << 16 | row}) so a single value persists and travels over the wire. A desktop lays icons out
 * top-down within a column, then continues in the next column to the right. An icon the player has dropped
 * somewhere ("pinned") keeps its stored cell; the rest flow into the first cell no pinned icon already
 * claims. Resolving both kinds in one pass means the draw and the hit-test always agree on where an icon is.
 */
public final class DesktopIconLayout {

    private DesktopIconLayout() {
    }

    /** Packs a grid column and row into a single cell value. */
    public static int pack(final int column, final int row) {
        return (column << 16) | (row & 0xFFFF);
    }

    /** The grid column of a packed cell. */
    public static int col(final int packed) {
        return packed >> 16;
    }

    /** The grid row of a packed cell. */
    public static int row(final int packed) {
        return packed & 0xFFFF;
    }

    /**
     * Resolves the packed grid cell of each of {@code keys} (in order), given the icons pinned to a stored
     * cell in {@code pinned} and a column height of {@code perColumn} rows.
     *
     * <p>A pinned icon keeps its cell, with the row clamped into {@code [0, perColumn)} and a negative column
     * pulled back to 0, so a stale cell from a taller monitor still lands on the grid. Unpinned icons take the
     * first free cell in column-major order, skipping any cell a pinned icon occupies. The result is parallel
     * to {@code keys}.
     *
     * @param keys      the icons to place, in display order
     * @param pinned    each pinned icon's stored packed cell, keyed by the same id used in {@code keys}
     * @param perColumn the number of rows in a column (at least 1)
     * @return the packed cell for each key, in the same order as {@code keys}
     */
    public static int[] resolve(final List<String> keys, final Map<String, Integer> pinned,
                                final int perColumn) {
        final int cols = Math.max(1, perColumn);
        final int[] cells = new int[keys.size()];
        final boolean[] isPinned = new boolean[keys.size()];
        final Set<Integer> taken = new HashSet<>();
        for (int i = 0; i < keys.size(); i++) {
            final Integer packed = pinned.get(keys.get(i));
            if (packed != null) {
                final int c = Math.max(0, col(packed));
                final int r = Math.max(0, Math.min(cols - 1, row(packed)));
                final int cell = pack(c, r);
                cells[i] = cell;
                isPinned[i] = true;
                taken.add(cell);
            }
        }
        int flow = 0;
        for (int i = 0; i < keys.size(); i++) {
            if (isPinned[i]) {
                continue;
            }
            int cell;
            do {
                cell = pack(flow / cols, flow % cols);
                flow++;
            } while (taken.contains(cell));
            cells[i] = cell;
            taken.add(cell);
        }
        return cells;
    }
}
