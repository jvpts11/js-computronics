/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.client.gui.widget;

import dev.jstech.core.client.gui.logic.GraphScale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A line graph of a rolling series of values (FE over time, throughput, temperature, etc.).
 */
public final class GraphPanelWidget extends AbstractWidget {

    private final double[] values;
    private int count;
    private int head;
    private final int lineColor;

    public GraphPanelWidget(
            final int x,
            final int y,
            final int width,
            final int height,
            final int capacity,
            final int lineColor,
            final Component message) {
        super(x, y, width, height, message);
        if (capacity < 2) {
            throw new IllegalArgumentException("capacity must be >= 2; got " + capacity);
        }
        this.values = new double[capacity];
        this.lineColor = lineColor;
    }

    public void pushValue(final double value) {
        if (count < values.length) {
            values[(head + count) % values.length] = value;
            count++;
        } else {
            values[head] = value;
            head = (head + 1) % values.length;
        }
    }

    public int sampleCount() {
        return count;
    }

    private double sampleAt(final int logicalIndex) {
        return values[(head + logicalIndex) % values.length];
    }

    @Override
    protected void renderWidget(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        // Panel background + border.
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF101010);

        if (count < 2) {
            return; // need at least two points to draw a line
        }

        // Build scale from current samples.
        final double[] snapshot = new double[count];
        for (int i = 0; i < count; i++) {
            snapshot[i] = sampleAt(i);
        }
        final GraphScale scale = GraphScale.fromData(snapshot);

        // Connect consecutive points. X spreads samples across the width.
        final double stepX = (double) width / (count - 1);
        for (int i = 0; i < count - 1; i++) {
            final int x1 = getX() + (int) Math.round(i * stepX);
            final int x2 = getX() + (int) Math.round((i + 1) * stepX);
            final int y1 = getY() + (int) Math.round(scale.valueToY(snapshot[i], height));
            final int y2 = getY() + (int) Math.round(scale.valueToY(snapshot[i + 1], height));
            drawLine(graphics, x1, y1, x2, y2, lineColor);
        }
    }

    private static void drawLine(
            final GuiGraphics graphics,
            final int x1, final int y1,
            final int x2, final int y2,
            final int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int cx = x1;
        int cy = y1;
        while (true) {
            graphics.fill(cx, cy, cx + 1, cy + 1, color);
            if (cx == x2 && cy == y2) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}