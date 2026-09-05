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
 * A row of tabs of equal width, one of them selected, with the skin's separator under the row.
 */
public final class TabStrip extends UiComponent {

    private final List<String> labels;
    private int selected;
    private IntConsumer onSelect = i -> { };
    private boolean underline = true;

    public TabStrip(final List<String> labels) {
        this.labels = List.copyOf(labels);
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

    private int tabWidth() {
        return Math.max(1, width() / Math.max(1, labels.size()));
    }

    /** The centre of tab {@code index}, where a test clicks it. */
    public int[] tabCenter(final int index) {
        final int tw = tabWidth();
        return new int[] {x() + index * tw + tw / 2, y() + height() / 2};
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        final int tw = tabWidth();
        for (int i = 0; i < labels.size(); i++) {
            ctx.skin().tab(g, ctx.font(), x() + i * tw, y(), tw, height(), labels.get(i), i == selected);
        }
        if (underline) {
            g.fill(x(), y() + height() - 1, x() + width(), y() + height(), ctx.skin().edge());
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        final int index = Math.min(labels.size() - 1, Math.max(0, (int) (mx - x()) / tabWidth()));
        if (index != selected) {
            selected = index;
            onSelect.accept(index);
        }
        return true;
    }
}
