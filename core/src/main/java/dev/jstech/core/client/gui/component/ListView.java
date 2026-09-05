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

import java.util.List;
import java.util.function.Supplier;

/**
 * A scrolling list of rows of one height. The items come from a supplier read every frame, so the list shows
 * live data; the rows are drawn by a renderer given the hover and selection state, so the same list shows
 * files, machines or operations. The wheel scrolls a row at a time and never past the end.
 *
 * @param <T> the item type
 */
public final class ListView<T> extends UiComponent {

    /** Draws one row in its rectangle. */
    @FunctionalInterface
    public interface RowRenderer<T> {
        void render(GuiGraphics g, UiContext ctx, T item, int index, int x, int y, int w, int h, boolean hovered,
                    boolean selected);
    }

    /** A click on a row. */
    @FunctionalInterface
    public interface RowClick {
        void click(int index, int button);
    }

    private final Supplier<List<T>> items;
    private final int rowHeight;
    private final RowRenderer<T> renderer;
    private RowClick onClick = (index, button) -> { };
    private boolean selectable;
    private int selected = -1;
    private int scroll;
    private int padding;

    public ListView(final Supplier<List<T>> items, final int rowHeight, final RowRenderer<T> renderer) {
        this.items = items;
        this.rowHeight = Math.max(1, rowHeight);
        this.renderer = renderer;
    }

    public ListView<T> setOnClick(final RowClick action) {
        onClick = action;
        return this;
    }

    /** Whether a click keeps the row selected (and a click on empty space clears the selection). */
    public ListView<T> setSelectable(final boolean value) {
        selectable = value;
        return this;
    }

    /** Pixels kept empty inside the bounds, around the rows. */
    public ListView<T> setPadding(final int value) {
        padding = Math.max(0, value);
        return this;
    }

    public int selected() {
        return selected;
    }

    public ListView<T> setSelected(final int index) {
        selected = index;
        return this;
    }

    public int scroll() {
        return scroll;
    }

    public ListView<T> setScroll(final int value) {
        scroll = Math.max(0, value);
        return this;
    }

    public int rowHeight() {
        return rowHeight;
    }

    public List<T> items() {
        return items.get();
    }

    /** How many rows fit in the bounds. */
    public int visibleRows() {
        return Math.max(1, (height() - padding * 2) / rowHeight);
    }

    /** The centre of item {@code index}'s row as laid out right now, where a test clicks it. */
    public int[] rowCenter(final int index) {
        return new int[] {x() + width() / 2, y() + padding + (index - scroll) * rowHeight + rowHeight / 2};
    }

    private void clampScroll(final int count) {
        scroll = new ScrollState(count, visibleRows(), scroll).offset();
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        final List<T> list = items.get();
        clampScroll(list.size());
        if (selected >= list.size()) {
            selected = -1;
        }
        final int visible = visibleRows();
        final int rx = x() + padding;
        final int rw = width() - padding * 2;
        for (int i = 0; i < visible; i++) {
            final int index = scroll + i;
            if (index >= list.size()) {
                break;
            }
            final int ry = y() + padding + i * rowHeight;
            final boolean hovered = enabled() && ctx.over(rx, ry, rw, rowHeight);
            renderer.render(g, ctx, list.get(index), index, rx, ry, rw, rowHeight, hovered, index == selected);
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        final List<T> list = items.get();
        final int row = (int) (my - y() - padding) / rowHeight;
        final int index = scroll + row;
        if (row < 0 || index >= list.size()) {
            if (selectable) {
                selected = -1;
            }
            return true;
        }
        if (selectable) {
            selected = index;
        }
        onClick.click(index, button);
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        scroll = Math.max(0, scroll + (delta > 0 ? -1 : 1));
        clampScroll(items.get().size());
        return true;
    }
}
