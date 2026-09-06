/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A panel whose children take more height than it gets: it shows a window onto them, moves that window
 * with the wheel and draws a thumb saying where it is. The owner lays the children out through
 * {@link #contentY}, in the same coordinates as everything else, and tells the panel how tall the whole
 * content is; the panel clips what it draws to its bounds and ignores clicks and tooltips outside them.
 */
public class ScrollPanel extends Panel {

    private static final int THUMB_W = 3;

    private int scroll;
    private int contentHeight;
    private int step = 10;

    /** The height of everything laid out in the panel, from which how far it scrolls follows. */
    public ScrollPanel setContentHeight(final int value) {
        contentHeight = Math.max(0, value);
        setScroll(scroll);
        return this;
    }

    public int contentHeight() {
        return contentHeight;
    }

    /** How many pixels one notch of the wheel moves. */
    public ScrollPanel setStep(final int value) {
        step = Math.max(1, value);
        return this;
    }

    public int scroll() {
        return scroll;
    }

    public ScrollPanel setScroll(final int value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        return this;
    }

    /** The largest scroll that still keeps the bottom of the content in view. */
    public int maxScroll() {
        return Math.max(0, contentHeight - height());
    }

    /** The y a child sitting {@code localY} pixels down the content is laid out at right now. */
    public int contentY(final int localY) {
        return y() + localY - scroll;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        setScroll(scroll);
        Draw.pushScissor(g, x(), y(), right(), bottom());
        super.render(g, ctx);
        Draw.popScissor(g);
        final int max = maxScroll();
        if (max > 0) {
            final int trackH = height() - 2;
            final int thumbH = Math.max(8, trackH * height() / (contentHeight + 2));
            final int thumbY = y() + 1 + (trackH - thumbH) * scroll / max;
            ctx.skin().scrollThumb(g, right() - THUMB_W - 1, thumbY, THUMB_W, thumbH);
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        return contains(mx, my) && super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        if (contains(mx, my) && super.mouseScrolled(mx, my, delta)) {
            return true;
        }
        if (maxScroll() == 0) {
            return false;
        }
        setScroll(scroll + (delta > 0 ? -step : step));
        return true;
    }

    @Override
    public List<Component> tooltip(final double mx, final double my) {
        return contains(mx, my) ? super.tooltip(mx, my) : List.of();
    }
}
