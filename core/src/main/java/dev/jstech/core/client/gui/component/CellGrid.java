/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import dev.jstech.core.client.gui.logic.ScrollState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

/**
 * A grid of square cells, the kind a recipe or an inventory is laid out in: each cell is drawn as an empty
 * well with a border, lit under the cursor, and a renderer puts the content in it. A grid taller than the
 * rows it shows scrolls by whole rows on the wheel, with cues beside it saying there is more.
 */
public final class CellGrid extends UiComponent {

    /** Draws the content of one cell. */
    @FunctionalInterface
    public interface CellRenderer {
        void render(GuiGraphics g, UiContext ctx, int index, int x, int y, int size, boolean hovered);
    }

    /** A click on a cell. */
    @FunctionalInterface
    public interface CellClick {
        void click(int index, int button, boolean shift);
    }

    /** Which side of the grid the scroll cues sit on. */
    public enum Cues { NONE, LEFT, RIGHT }

    private final int columns;
    private final int visibleRows;
    private final int cell;
    private int totalRows;
    private int scroll;
    private CellRenderer renderer = (g, ctx, index, x, y, size, hovered) -> { };
    private CellClick onClick = (index, button, shift) -> { };
    private IntPredicate marked = index -> false;
    private Cues cues = Cues.NONE;

    /** A grid of {@code columns} by {@code visibleRows} cells of {@code cell} pixels; {@code totalRows} in all. */
    public CellGrid(final int columns, final int visibleRows, final int totalRows, final int cell) {
        this.columns = Math.max(1, columns);
        this.visibleRows = Math.max(1, visibleRows);
        this.totalRows = Math.max(this.visibleRows, totalRows);
        this.cell = Math.max(1, cell);
    }

    public CellGrid setRenderer(final CellRenderer value) {
        renderer = value;
        return this;
    }

    public CellGrid setOnClick(final CellClick action) {
        onClick = action;
        return this;
    }

    /** Which cells are drawn with the accent border (a cell with a tag, an estimated amount). */
    public CellGrid setMarked(final IntPredicate predicate) {
        marked = predicate;
        return this;
    }

    public CellGrid setCues(final Cues value) {
        cues = value;
        return this;
    }

    public CellGrid setTotalRows(final int value) {
        totalRows = Math.max(visibleRows, value);
        return this;
    }

    /** Places the grid at ({@code x}, {@code y}); its size follows from the cells it shows. */
    public CellGrid place(final int x, final int y) {
        setBounds(x, y, columns * cell, visibleRows * cell);
        return this;
    }

    public int columns() {
        return columns;
    }

    public int cellSize() {
        return cell;
    }

    public int scroll() {
        return scroll;
    }

    public CellGrid setScroll(final int value) {
        scroll = new ScrollState(totalRows, visibleRows, value).offset();
        return this;
    }

    /** The rectangle of cell {@code index} as laid out now, or null when it is scrolled out of view. */
    @Nullable
    public int[] cellRect(final int index) {
        final int row = index / columns - scroll;
        if (index < 0 || row < 0 || row >= visibleRows) {
            return null;
        }
        return new int[] {x() + (index % columns) * cell, y() + row * cell, cell, cell};
    }

    /** The centre of cell {@code index} as laid out now, where a test clicks it; the grid's centre if hidden. */
    public int[] cellCenter(final int index) {
        final int[] r = cellRect(index);
        return r == null ? center() : new int[] {r[0] + cell / 2, r[1] + cell / 2};
    }

    /** The index of the cell under the point, or -1 outside the grid. */
    public int cellAt(final double mx, final double my) {
        if (!contains(mx, my)) {
            return -1;
        }
        final int col = (int) (mx - x()) / cell;
        final int row = (int) (my - y()) / cell;
        return (scroll + row) * columns + col;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        setScroll(scroll);
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < columns; col++) {
                final int index = (scroll + row) * columns + col;
                final int cx = x() + col * cell;
                final int cy = y() + row * cell;
                final boolean hovered = enabled() && ctx.over(cx, cy, cell, cell);
                g.fill(cx, cy, cx + cell, cy + cell, ctx.skin().fieldBg());
                Draw.outline(g, cx, cy, cell, cell, marked.test(index) ? ctx.skin().accent() : ctx.skin().edge());
                if (hovered) {
                    g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, ctx.skin().listHover());
                }
                renderer.render(g, ctx, index, cx, cy, cell, hovered);
            }
        }
        if (cues != Cues.NONE) {
            final int cueX = cues == Cues.LEFT ? x() - 7 : x() + width() + 2;
            if (scroll > 0) {
                g.drawString(ctx.font(), "^", cueX, y(), ctx.skin().dim(), false);
            }
            if (scroll + visibleRows < totalRows) {
                g.drawString(ctx.font(), "v", cueX, y() + height() - 9, ctx.skin().dim(), false);
            }
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        final int index = cellAt(mx, my);
        if (index < 0) {
            return false;
        }
        onClick.click(index, button, Screen.hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        if (totalRows <= visibleRows) {
            return false;
        }
        setScroll(scroll + (delta > 0 ? -1 : 1));
        return true;
    }
}
