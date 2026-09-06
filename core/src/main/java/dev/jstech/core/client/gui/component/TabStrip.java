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
 * A row of tabs, one of them selected, with the skin's separator under the row. The tabs share the width
 * equally, or each takes what its label needs when {@link #fitToLabels} is set.
 */
public final class TabStrip extends UiComponent {

    private final List<String> labels;
    private int selected;
    private IntConsumer onSelect = i -> { };
    private boolean underline = true;
    private int labelPadding = -1;
    /** The widths of the tabs as last drawn; equal shares until the first frame measures the labels. */
    private int[] widths = new int[0];

    public TabStrip(final List<String> labels) {
        this.labels = List.copyOf(labels);
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

    public int selected() {
        return selected;
    }

    /** Selects a tab without firing the callback, as a state refresh from outside does. */
    public TabStrip setSelected(final int index) {
        selected = Math.max(0, Math.min(labels.size() - 1, index));
        return this;
    }

    public int count() {
        return labels.size();
    }

    /** The width of tab {@code index} as laid out now. */
    private int tabWidth(final int index) {
        if (labelPadding >= 0 && index < widths.length) {
            return widths[index];
        }
        return Math.max(1, width() / Math.max(1, labels.size()));
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
        if (labelPadding >= 0) {
            final int[] measured = new int[labels.size()];
            for (int i = 0; i < labels.size(); i++) {
                measured[i] = ctx.font().width(labels.get(i)) + labelPadding;
            }
            widths = measured;
        }
        for (int i = 0; i < labels.size(); i++) {
            ctx.skin().tab(g, ctx.font(), tabX(i), y(), tabWidth(i), height(), labels.get(i), i == selected);
        }
        if (underline) {
            g.fill(x(), y() + height() - 1, x() + width(), y() + height(), ctx.skin().edge());
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        int index = labels.size() - 1;
        for (int i = 0; i < labels.size(); i++) {
            if (mx < tabX(i) + tabWidth(i)) {
                index = i;
                break;
            }
        }
        if (index != selected) {
            selected = index;
            onSelect.accept(index);
        }
        return true;
    }
}
