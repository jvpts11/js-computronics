/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.widget;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal base for list-style widgets: stores a typed item list, validates and holds the row height, and
 * delegates narration. Subclasses own their pagination / scroll state and their rendering strategy.
 *
 * <p>This class is package-private — public widget contracts are {@link DataTableWidget} and
 * {@link ScrollableListWidget}. Callers outside this package interact only with those types.
 */
abstract class RowListWidget<T> extends AbstractWidget {

    protected final List<T> itemList = new ArrayList<>();
    protected final int rowHeight;

    protected RowListWidget(
            final int x,
            final int y,
            final int width,
            final int height,
            final int rowHeight,
            final Component message) {
        super(x, y, width, height, message);
        if (rowHeight < 1) {
            throw new IllegalArgumentException("rowHeight must be >= 1; got " + rowHeight);
        }
        this.rowHeight = rowHeight;
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
