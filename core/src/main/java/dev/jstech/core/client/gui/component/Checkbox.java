/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A small box with a label that turns something on or off. The state lives with its owner and is read every
 * frame; a click asks the owner to flip it.
 */
public final class Checkbox extends UiComponent {

    private static final int BOX = 7;

    private final Supplier<String> label;
    private final BooleanSupplier checked;
    private final Runnable onToggle;
    private float labelScale = 1f;

    public Checkbox(final Supplier<String> label, final BooleanSupplier checked, final Runnable onToggle) {
        this.label = label;
        this.checked = checked;
        this.onToggle = onToggle;
    }

    /** Draws the label smaller than the font, for a dense list of choices. */
    public Checkbox setLabelScale(final float scale) {
        labelScale = scale;
        return this;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        final boolean on = checked.getAsBoolean();
        final int by = y() + (height() - BOX) / 2;
        g.fill(x(), by, x() + BOX, by + BOX, on ? ctx.skin().accent() : ctx.skin().fieldBg());
        Draw.outline(g, x(), by, BOX, BOX, ctx.skin().edge());
        if (on) {
            g.drawString(ctx.font(), "x", x() + 1, by - 1, 0xFFFFFFFF, false);
        }
        final int textX = x() + BOX + 4;
        final int fits = (int) ((width() - BOX - 4) / labelScale);
        final String text = Texts.trim(ctx.font(), label.get(), fits);
        final int th = Math.round(7 * labelScale);
        Texts.scaled(g, ctx.font(), text, textX, y() + (height() - th) / 2, labelScale, ctx.skin().text());
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        onToggle.run();
        return true;
    }
}
