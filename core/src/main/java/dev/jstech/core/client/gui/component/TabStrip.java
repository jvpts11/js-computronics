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
import java.util.function.Supplier;

/**
 * A row of tabs, one of them selected, with the skin's separator under the row. The tabs share the width
 * equally, or each takes what its label needs when {@link #fitToLabels} is set.
 */
public final class TabStrip extends UiComponent {

    private final Supplier<List<String>> labels;
    private int selected;
    private IntConsumer onSelect = i -> { };
    private boolean underline = true;
    private int labelPadding = -1;
    /** The widths of the tabs as last drawn; equal shares until the first frame measures the labels. */
    private int[] widths = new int[0];

    /** A strip whose tabs never change: the sections of a screen. */
    public TabStrip(final List<String> labels) {
        final List<String> fixed = List.copyOf(labels);
        this.labels = () -> fixed;
    }

    /**
     * A strip whose tabs come and go: the files an editor has open. The labels are read as they are
     * drawn, so opening or closing one needs nothing said here.
     */
    public TabStrip(final Supplier<List<String>> labels) {
        this.labels = labels;
    }

    private List<String> labels() {
        final List<String> current = this.labels.get();
        return current == null ? List.of() : current;
    }

    /** Gives each tab the width of its label plus {@code padding}, instead of an equal share of the strip. */
    public TabStrip fitToLabels(final int padding) {
        labelPadding = Math.max(0, padding);
        return this;
    }

    public TabStrip setOnSelect(final IntConsumer action) {
        onSelect = action;
        return this;
    }

    /** Whether to draw the separator line along the bottom of the strip. */
    public TabStrip setUnderline(final boolean value) {
        underline = value;
        return this;
    }

    /** Room for the close mark at the right of a tab that can be closed. */
    private static final int CLOSE_W = 9;
    private IntConsumer onClose;

    /**
     * Gives every tab a close mark at its right, and says what closing one does.
     *
     * <p>The mark takes the click that lands on it, and so does the middle button anywhere on the tab,
     * the way an editor's tabs close; neither selects the tab first.
     */
    public TabStrip setCloseable(final IntConsumer action) {
        onClose = action;
        return this;
    }

    public int selected() {
        return selected;
    }

    /** Selects a tab without firing the callback, as a state refresh from outside does. */
    public TabStrip setSelected(final int index) {
        selected = Math.max(0, Math.min(labels().size() - 1, index));
        return this;
    }

    public int count() {
        return labels().size();
    }

    /** The width of tab {@code index} as laid out now. */
    private int tabWidth(final int index) {
        if (labelPadding >= 0 && index < widths.length) {
            return widths[index];
        }
        return Math.max(1, width() / Math.max(1, labels().size()));
    }

    /** The left edge of tab {@code index} as laid out now. */
    private int tabX(final int index) {
        int tx = x();
        for (int i = 0; i < index; i++) {
            tx += tabWidth(i);
        }
        return tx;
    }

    /** The centre of tab {@code index}, where a test clicks it. */
    public int[] tabCenter(final int index) {
        return new int[] {tabX(index) + tabWidth(index) / 2, y() + height() / 2};
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        final List<String> current = labels();
        final int close = onClose == null ? 0 : CLOSE_W;
        if (labelPadding >= 0) {
            final int[] measured = new int[current.size()];
            for (int i = 0; i < current.size(); i++) {
                measured[i] = ctx.font().width(current.get(i)) + labelPadding + close;
            }
            widths = measured;
        }
        for (int i = 0; i < current.size(); i++) {
            final int tx = tabX(i);
            final int tw = tabWidth(i);
            ctx.skin().tab(g, ctx.font(), tx, y(), tw - close, height(), current.get(i), i == selected);
            if (close > 0) {
                // The mark is part of the tab, drawn on the same ground, lit when the mouse is over it.
                final boolean over = ctx.over(tx + tw - close, y(), close, height());
                g.drawString(ctx.font(), "x", tx + tw - close + 2, y() + (height() - 7) / 2,
                        over ? ctx.skin().text() : ctx.skin().dim(), false);
            }
        }
        if (underline) {
            g.fill(x(), y() + height() - 1, x() + width(), y() + height(), ctx.skin().edge());
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        final List<String> current = labels();
        if (current.isEmpty()) {
            // A strip whose tabs come and go can be empty, and an empty strip has nothing to select.
            return true;
        }
        int index = current.size() - 1;
        for (int i = 0; i < current.size(); i++) {
            if (mx < tabX(i) + tabWidth(i)) {
                index = i;
                break;
            }
        }
        if (onClose != null) {
            final boolean onMark = mx >= tabX(index) + tabWidth(index) - CLOSE_W;
            if (button == 2 || (button == 0 && onMark)) {
                onClose.accept(index);
                return true;
            }
        }
        if (index != selected) {
            selected = index;
            onSelect.accept(index);
        }
        return true;
    }
}
