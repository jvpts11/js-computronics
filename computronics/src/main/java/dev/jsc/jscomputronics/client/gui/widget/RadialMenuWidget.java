/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.widget;

import dev.jsc.jscomputronics.client.gui.logic.RadialGeometry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.OptionalInt;

/**
 * A radial (pie) menu of labeled segments, highlighting the one under the cursor.
 */
public final class RadialMenuWidget extends AbstractWidget {

    private final List<Component> segmentLabels;
    private final double innerRadius;
    private final double outerRadius;
    private int selectedSegment = -1;

    public RadialMenuWidget(
            final int x,
            final int y,
            final int size,
            final List<Component> segmentLabels,
            final double innerRadius,
            final double outerRadius,
            final Component message) {
        super(x, y, size, size, message);
        if (segmentLabels.isEmpty()) {
            throw new IllegalArgumentException("segmentLabels must not be empty");
        }
        if (innerRadius < 0 || outerRadius <= innerRadius) {
            throw new IllegalArgumentException(
                    "require 0 <= innerRadius < outerRadius");
        }
        this.segmentLabels = List.copyOf(segmentLabels);
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
    }

    private int centerX() {
        return getX() + width / 2;
    }

    private int centerY() {
        return getY() + height / 2;
    }

    public int selectedSegment() {
        return selectedSegment;
    }

    @Override
    protected void renderWidget(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        // Update selection from cursor position.
        final double dx = mouseX - centerX();
        final double dy = mouseY - centerY();
        final OptionalInt seg = RadialGeometry.segmentAt(
                dx, dy, segmentLabels.size(), innerRadius, outerRadius);
        selectedSegment = seg.orElse(-1);

        // Draw each segment's label at the midpoint angle, radius =
        // average of inner/outer. Segment 0 at top, clockwise.
        final double labelRadius = (innerRadius + outerRadius) / 2.0;
        final double segSize = (Math.PI * 2) / segmentLabels.size();
        for (int i = 0; i < segmentLabels.size(); i++) {
            final double midAngle = (i + 0.5) * segSize; // top-origin clockwise
            // Convert back to screen coords: x = sin(a), y = -cos(a).
            final int lx = centerX() + (int) Math.round(Math.sin(midAngle) * labelRadius);
            final int ly = centerY() - (int) Math.round(Math.cos(midAngle) * labelRadius);
            final int color = (i == selectedSegment) ? 0xFFFFFF00 : 0xFFFFFFFF;
            graphics.drawCenteredString(
                    net.minecraft.client.Minecraft.getInstance().font,
                    segmentLabels.get(i),
                    lx, ly - 4, color);
        }
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}