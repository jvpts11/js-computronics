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
    /** How close to a column's left edge a press has to land to take hold of it. */
    private static final int GRIP = 3;
    /** The column whose left edge the mouse is dragging, or -1. */
    private int dragging = -1;
    private java.util.function.BiConsumer<Integer, Integer> onResize = (column, edge) -> { };

    /**
     * Says what happens when a column's left edge is dragged: called with the column and where its
     * edge now is, for the owner to lay the columns out again.
     */
    public ColumnHeader setOnResize(final java.util.function.BiConsumer<Integer, Integer> action) {
        onResize = action;
        return this;
    }

    /** Whether a column edge is being dragged, so the owner keeps feeding the mouse here. */
    public boolean dragging() {
        return dragging >= 0;
    }

    /** The column whose left edge is under {@code mx}, or -1; the first column's edge does not move. */
    private int edgeAt(final double mx) {
        for (int i = 1; i < columnX.length && i < labels.size(); i++) {
            if (Math.abs(mx - columnX[i]) <= GRIP) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseDragged(final double mx, final double my, final int button) {
        if (dragging < 0) {
            return false;
        }
        onResize.accept(dragging, (int) mx);
        return true;
    }

    @Override
    public boolean mouseReleased(final double mx, final double my, final int button) {
        final boolean was = dragging >= 0;
        dragging = -1;
        return was;
    }

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
        // A press on the edge between two columns takes hold of it rather than sorting.
        final int edge = edgeAt(mx);
        if (edge >= 0 && button == 0) {
            dragging = edge;
            return true;
        }
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
