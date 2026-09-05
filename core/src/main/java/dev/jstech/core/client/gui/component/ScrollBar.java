/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A thin vertical scrollbar beside something that scrolls in steps: a track with the skin's thumb, sized by
 * how much of the whole is in view. It reads the position and the range from the thing it scrolls every
 * frame and hands a click or a drag on it back as a new position.
 */
public final class ScrollBar extends UiComponent {

    private static final int MIN_THUMB = 8;

    private final IntSupplier max;
    private final IntSupplier value;
    private final IntConsumer onChange;

    /**
     * @param max      the largest position, read each frame; the bar draws nothing at or below zero
     * @param value    the current position, read each frame
     * @param onChange fires with the position a click or a drag asks for
     */
    public ScrollBar(final IntSupplier max, final IntSupplier value, final IntConsumer onChange) {
        this.max = max;
        this.value = value;
        this.onChange = onChange;
    }

    private int thumbHeight(final int range) {
        return Math.max(MIN_THUMB, height() / (range + 1));
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        final int range = max.getAsInt();
        if (range <= 0) {
            return;
        }
        g.fill(x(), y(), right(), bottom(), ctx.skin().edge());
        final int th = thumbHeight(range);
        final int ty = y() + (height() - th) * Math.max(0, Math.min(range, value.getAsInt())) / range;
        ctx.skin().scrollThumb(g, x(), ty, width(), th);
    }

    private void jumpTo(final double my) {
        final int range = max.getAsInt();
        if (range <= 0) {
            return;
        }
        final int travel = Math.max(1, height() - thumbHeight(range));
        final int position = (int) Math.round((my - y() - thumbHeight(range) / 2.0) * range / travel);
        onChange.accept(Math.max(0, Math.min(range, position)));
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (max.getAsInt() <= 0) {
            return false;
        }
        jumpTo(my);
        return true;
    }

    @Override
    public boolean mouseDragged(final double mx, final double my, final int button) {
        jumpTo(my);
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        final int range = max.getAsInt();
        if (range <= 0) {
            return false;
        }
        onChange.accept(Math.max(0, Math.min(range, value.getAsInt() + (delta > 0 ? -1 : 1))));
        return true;
    }
}
