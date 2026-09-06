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
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * A grid of cells, the kind a recipe, an inventory or an icon view is laid out in: each cell is drawn as an
 * empty well with a border (or as a plain row background, for tiles), lit under the cursor, and a renderer
 * puts the content in it. A grid taller than the rows it shows scrolls by whole rows on the wheel, with cues
 * beside it saying there is more.
 */
public final class CellGrid extends UiComponent {

    /** Draws the content of one cell in its rectangle. */
    @FunctionalInterface
    public interface CellRenderer {
        void render(GuiGraphics g, UiContext ctx, int index, int x, int y, int w, int h, boolean hovered);
    }

    /** A click on a cell. */
    @FunctionalInterface
    public interface CellClick {
        void click(int index, int button, boolean shift);
    }

    /** Which side of the grid the scroll cues sit on. */
    public enum Cues { NONE, LEFT, RIGHT }

    private int columns;
    private int visibleRows;
    private final int cellW;
    private final int cellH;
    private int totalRows;
    private int scroll;
    private int inset;
    private int cellCount = Integer.MAX_VALUE;
    private boolean wells = true;
    private CellRenderer renderer = (g, ctx, index, x, y, w, h, hovered) -> { };
    private CellClick onClick = (index, button, shift) -> { };
    private IntPredicate marked = index -> false;
    private IntPredicate selected = index -> false;
    private IntFunction<List<Component>> tooltips = index -> List.of();
    private Cues cues = Cues.NONE;

    /** A grid of {@code columns} by {@code visibleRows} square cells of {@code cell} pixels; {@code totalRows} in all. */
    public CellGrid(final int columns, final int visibleRows, final int totalRows, final int cell) {
        this(columns, visibleRows, totalRows, cell, cell);
    }

    /** A grid of {@code columns} by {@code visibleRows} cells {@code cellW} wide and {@code cellH} tall. */
    public CellGrid(final int columns, final int visibleRows, final int totalRows, final int cellW, final int cellH) {
        this.columns = Math.max(1, columns);
        this.visibleRows = Math.max(1, visibleRows);
        this.totalRows = Math.max(this.visibleRows, totalRows);
        this.cellW = Math.max(1, cellW);
        this.cellH = Math.max(1, cellH);
    }

    /** Pixels left empty at the right and bottom of every cell, so the wells sit apart like inventory slots. */
    public CellGrid setInset(final int value) {
        inset = Math.max(0, Math.min(Math.min(cellW, cellH) - 1, value));
        return this;
    }

    /**
     * Whether cells are drawn as sunken wells with a border (an inventory) or as plain tiles that only show a
     * background when hovered or selected (an icon view).
     */
    public CellGrid setWells(final boolean value) {
        wells = value;
        return this;
    }

    /** Which cells are drawn selected; only tiles ({@link #setWells} false) show it. */
    public CellGrid setSelected(final IntPredicate predicate) {
        selected = predicate;
        return this;
    }

    /** How many cells hold something: the ones past that are neither drawn nor clickable. */
    public CellGrid setCellCount(final int value) {
        cellCount = Math.max(0, value);
        return this;
    }

    public CellGrid setColumns(final int value) {
        columns = Math.max(1, value);
        return this;
    }

    public CellGrid setVisibleRows(final int value) {
        visibleRows = Math.max(1, value);
        totalRows = Math.max(visibleRows, totalRows);
        return this;
    }

    public int visibleRows() {
        return visibleRows;
    }

    public int totalRows() {
        return totalRows;
    }

    /** The largest scroll that still keeps the last row in view. */
    public int maxScroll() {
        return Math.max(0, totalRows - visibleRows);
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

    /** The tooltip lines for a cell, shown while the cursor rests on it. */
    public CellGrid setTooltip(final IntFunction<List<Component>> value) {
        tooltips = value;
        return this;
    }

    @Override
    public List<Component> tooltip(final double mx, final double my) {
        final int index = cellAt(mx, my);
        return index < 0 ? List.of() : tooltips.apply(index);
    }

    public CellGrid setTotalRows(final int value) {
        totalRows = Math.max(visibleRows, value);
        return this;
    }

    /** Places the grid at ({@code x}, {@code y}); its size follows from the cells it shows. */
    public CellGrid place(final int x, final int y) {
        setBounds(x, y, columns * cellW, visibleRows * cellH);
        return this;
    }

    public int columns() {
        return columns;
    }

    public int cellWidth() {
        return cellW;
    }

    public int cellHeight() {
        return cellH;
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
        return new int[] {x() + (index % columns) * cellW, y() + row * cellH, cellW - inset, cellH - inset};
    }

    /** The centre of cell {@code index} as laid out now, where a test clicks it; the grid's centre if hidden. */
    public int[] cellCenter(final int index) {
        final int[] r = cellRect(index);
        return r == null ? center() : new int[] {r[0] + cellW / 2, r[1] + cellH / 2};
    }

    /** The index of the cell under the point, or -1 outside the grid or past the last cell that holds something. */
    public int cellAt(final double mx, final double my) {
        if (!contains(mx, my)) {
            return -1;
        }
        final int col = (int) (mx - x()) / cellW;
        final int row = (int) (my - y()) / cellH;
        final int index = (scroll + row) * columns + col;
        return index < cellCount ? index : -1;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        setScroll(scroll);
        final int w = cellW - inset;
        final int h = cellH - inset;
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < columns; col++) {
                final int index = (scroll + row) * columns + col;
                if (index >= cellCount) {
                    break;
                }
                final int cx = x() + col * cellW;
                final int cy = y() + row * cellH;
                final boolean hovered = enabled() && ctx.over(cx, cy, w, h);
                if (wells) {
                    g.fill(cx, cy, cx + w, cy + h, ctx.skin().fieldBg());
                    Draw.outline(g, cx, cy, w, h, marked.test(index) ? ctx.skin().accent() : ctx.skin().edge());
                    if (hovered) {
                        g.fill(cx + 1, cy + 1, cx + w - 1, cy + h - 1, ctx.skin().listHover());
                    }
                } else {
                    ctx.skin().listRow(g, cx, cy, w, h, hovered, selected.test(index));
                }
                renderer.render(g, ctx, index, cx, cy, w, h, hovered);
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
        scrollBy(delta > 0 ? -1 : 1);
        return true;
    }

    /** Scrolls by {@code rows}, kept inside the range. */
    public void scrollBy(final int rows) {
        setScroll(scroll + rows);
    }
}
