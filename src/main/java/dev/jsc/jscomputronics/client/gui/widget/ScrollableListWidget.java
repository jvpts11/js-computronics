/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.widget;

import dev.jsc.jscomputronics.client.gui.logic.ScrollState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic scrollable list of rows of type {@code T}.
 */
public abstract class ScrollableListWidget<T> extends AbstractWidget {

    private final List<T> items = new ArrayList<>();
    private final int rowHeight;
    private ScrollState scroll;

    protected ScrollableListWidget(
            final int x,
            final int y,
            final int width,
            final int height,
            final int rowHeight,
            final Component message) {
        super(x, y, width, height, message);
        if (rowHeight < 1) {
            throw new IllegalArgumentException(
                    "rowHeight must be >= 1; got " + rowHeight);
        }
        this.rowHeight = rowHeight;
        final int visibleRows = Math.max(1, height / rowHeight);
        this.scroll = ScrollState.of(0, visibleRows);
    }

    public void setItems(final List<T> newItems) {
        items.clear();
        items.addAll(newItems);
        scroll = scroll.withTotalItems(items.size());
    }

    public ScrollState scrollState() {
        return scroll;
    }

    protected abstract void renderRow(
            GuiGraphics graphics,
            T item,
            int rowX, int rowY, int rowWidth, int rowHeight,
            boolean hovered, int mouseX, int mouseY);

    @Override
    protected void renderWidget(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        final int first = scroll.firstVisibleIndex();
        final int last = scroll.lastVisibleIndexExclusive();
        for (int i = first; i < last; i++) {
            final int rowY = getY() + (i - first) * rowHeight;
            final boolean hovered = mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= rowY && mouseY < rowY + rowHeight;
            renderRow(graphics, items.get(i),
                    getX(), rowY, width, rowHeight, hovered, mouseX, mouseY);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(
            final double mouseX,
            final double mouseY,
            final double scrollX,
            final double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || !scroll.isScrollable()) {
            return false;
        }
        if (scrollY > 0) {
            scroll = scroll.scrolledBy(-1);
        } else if (scrollY < 0) {
            scroll = scroll.scrolledBy(1);
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput output) {
        // Minimal narration; refined per concrete list in Phase 1+.
        this.defaultButtonNarrationText(output);
    }
}