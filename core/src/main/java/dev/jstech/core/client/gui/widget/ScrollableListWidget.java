/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.client.gui.widget;

import dev.jstech.core.client.gui.logic.ScrollState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A generic scrollable list of rows of type {@code T}.
 */
public abstract class ScrollableListWidget<T> extends RowListWidget<T> {

    private ScrollState scroll;

    protected ScrollableListWidget(
            final int x,
            final int y,
            final int width,
            final int height,
            final int rowHeight,
            final Component message) {
        super(x, y, width, height, rowHeight, message);
        final int visibleRows = Math.max(1, height / rowHeight);
        this.scroll = ScrollState.of(0, visibleRows);
    }

    public void setItems(final List<T> newItems) {
        itemList.clear();
        itemList.addAll(newItems);
        scroll = scroll.withTotalItems(itemList.size());
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
            renderRow(graphics, itemList.get(i),
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

}
