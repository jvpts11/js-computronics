/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.widget;

import dev.jsc.jscomputronics.client.gui.logic.PaginationState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A paginated table of rows of type {@code T} with fixed columns.
 */
public abstract class DataTableWidget<T> extends AbstractWidget {

    private final List<T> rows = new ArrayList<>();
    private final List<Component> columnHeaders;
    private final int headerHeight;
    private final int rowHeight;
    private PaginationState pagination;

    protected DataTableWidget(
            final int x,
            final int y,
            final int width,
            final int height,
            final List<Component> columnHeaders,
            final int headerHeight,
            final int rowHeight,
            final Component message) {
        super(x, y, width, height, message);
        if (columnHeaders.isEmpty()) {
            throw new IllegalArgumentException("columnHeaders must not be empty");
        }
        if (rowHeight < 1 || headerHeight < 1) {
            throw new IllegalArgumentException("row/header height must be >= 1");
        }
        this.columnHeaders = List.copyOf(columnHeaders);
        this.headerHeight = headerHeight;
        this.rowHeight = rowHeight;
        final int bodyHeight = Math.max(0, height - headerHeight);
        final int rowsPerPage = Math.max(1, bodyHeight / rowHeight);
        this.pagination = PaginationState.of(0, rowsPerPage);
    }

    public void setRows(final List<T> newRows) {
        rows.clear();
        rows.addAll(newRows);
        pagination = pagination.withTotalItems(rows.size()).toPage(0);
    }

    public PaginationState pagination() {
        return pagination;
    }

    public void nextPage() {
        pagination = pagination.nextPage();
    }

    public void prevPage() {
        pagination = pagination.prevPage();
    }

    public int columnCount() {
        return columnHeaders.size();
    }

    protected int columnX(final int col) {
        final int colWidth = width / columnHeaders.size();
        return getX() + col * colWidth;
    }

    protected abstract void renderCell(
            GuiGraphics graphics, T row, int col, int cellX, int cellY);

    @Override
    protected void renderWidget(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

        // Header row.
        graphics.fill(getX(), getY(), getX() + width, getY() + headerHeight, 0xFF2A2A2A);
        for (int col = 0; col < columnHeaders.size(); col++) {
            graphics.drawString(
                    net.minecraft.client.Minecraft.getInstance().font,
                    columnHeaders.get(col),
                    columnX(col) + 2,
                    getY() + (headerHeight - 8) / 2,
                    0xFFFFFFFF);
        }

        // Body rows for the current page.
        final int first = pagination.firstItemIndex();
        final int last = pagination.lastItemIndexExclusive();
        for (int i = first; i < last; i++) {
            final int rowY = getY() + headerHeight + (i - first) * rowHeight;
            // Alternating row background for readability.
            final int bg = ((i - first) % 2 == 0) ? 0xFF1A1A1A : 0xFF222222;
            graphics.fill(getX(), rowY, getX() + width, rowY + rowHeight, bg);
            for (int col = 0; col < columnHeaders.size(); col++) {
                renderCell(graphics, rows.get(i), col,
                        columnX(col) + 2, rowY + (rowHeight - 8) / 2);
            }
        }

        graphics.disableScissor();
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}