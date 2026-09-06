/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The header over a table of rows: one label per column, the sorted column marked with the direction, a
 * click on a column sorting by it and a second click turning the order around. A header that only names
 * the columns ({@link #setSortable} false) draws no arrow and takes no click. The columns' left edges are
 * given each frame, since they follow the width of the list they head.
 */
public final class ColumnHeader extends UiComponent {

    private final List<String> labels;
    private int[] columnX = new int[0];
    private int sortColumn;
    private boolean ascending = true;
    private boolean sortable = true;
    private IntConsumer onSort = column -> { };

    public ColumnHeader(final List<String> labels) {
        this.labels = List.copyOf(labels);
    }

    /** The left edge of each column's label, in the coordinates the header is laid out in. */
    public ColumnHeader setColumnX(final int... xs) {
        columnX = xs.clone();
        return this;
    }

    /** Whether a click sorts by the column; a header over a fixed-order table only names them. */
    public ColumnHeader setSortable(final boolean value) {
        sortable = value;
        return this;
    }

    /** The left edge of column {@code index} as laid out now, where the table's rows put its text. */
    public int columnX(final int index) {
        return index >= 0 && index < columnX.length ? columnX[index] : x();
    }

    /** Fires with the column index after a click changed the sort column or its direction. */
    public ColumnHeader setOnSort(final IntConsumer action) {
        onSort = action;
        return this;
    }

    public int sortColumn() {
        return sortColumn;
    }

    public boolean ascending() {
        return ascending;
    }

    public ColumnHeader setSort(final int column, final boolean up) {
        sortColumn = column;
        ascending = up;
        return this;
    }

    /** The column whose span holds {@code mx}: the last column that starts at or before it. */
    public int columnAt(final double mx) {
        int column = 0;
        for (int i = 0; i < columnX.length && i < labels.size(); i++) {
            if (mx >= columnX[i]) {
                column = i;
            }
        }
        return column;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        g.fill(x(), y(), right(), bottom(), ctx.skin().listHover());
        g.fill(x(), bottom() - 1, right(), bottom(), ctx.skin().edge());
        final String arrow = ascending ? " ^" : " v";
        for (int i = 0; i < labels.size() && i < columnX.length; i++) {
            g.drawString(ctx.font(), labels.get(i) + (sortable && i == sortColumn ? arrow : ""), columnX[i], y() + 1,
                    ctx.skin().dim(), false);
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (!sortable) {
            return false;
        }
        final int column = columnAt(mx);
        if (column == sortColumn) {
            ascending = !ascending;
        } else {
            sortColumn = column;
            ascending = true;
        }
        onSort.accept(column);
        return true;
    }
}
