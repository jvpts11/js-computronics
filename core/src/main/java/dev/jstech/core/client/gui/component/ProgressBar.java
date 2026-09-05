/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntSupplier;

/**
 * A bar filled with the accent from the left by a percentage read every frame.
 */
public final class ProgressBar extends UiComponent {

    private final IntSupplier percent;

    /** @param percent the fill, 0 to 100, read each frame */
    public ProgressBar(final IntSupplier percent) {
        this.percent = percent;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        ctx.skin().panel(g, x(), y(), width(), height());
        final int p = Math.max(0, Math.min(100, percent.getAsInt()));
        if (p > 0) {
            g.fill(x() + 1, y() + 1, x() + 1 + (width() - 2) * p / 100, bottom() - 1, ctx.skin().accent());
        }
    }
}
