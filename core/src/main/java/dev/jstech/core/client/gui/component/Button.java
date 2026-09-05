/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Supplier;

/**
 * A push button. It fires on the press, as every control of the desktops does, and stays drawn pressed until
 * the button is released so the press reads; a disabled button is drawn faded and takes nothing.
 */
public final class Button extends UiComponent {

    private Supplier<String> label;
    private Runnable onPress;
    private boolean primary;
    private boolean pressed;

    public Button(final String label, final Runnable onPress) {
        this(() -> label, onPress);
    }

    public Button(final Supplier<String> label, final Runnable onPress) {
        this.label = label;
        this.onPress = onPress;
    }

    public Button setLabel(final String value) {
        label = () -> value;
        return this;
    }

    public Button setLabel(final Supplier<String> value) {
        label = value;
        return this;
    }

    public String label() {
        return label.get();
    }

    public Button setOnPress(final Runnable action) {
        onPress = action;
        return this;
    }

    /** Whether this is the default action of its panel, drawn as such. */
    public Button setPrimary(final boolean value) {
        primary = value;
        return this;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        ctx.skin().button(g, ctx.font(), x(), y(), width(), height(), label.get(), hovered(ctx), pressed, primary);
        if (!enabled()) {
            Draw.disabled(g, x(), y(), width(), height());
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (button != 0) {
            return false;
        }
        pressed = true;
        onPress.run();
        return true;
    }

    @Override
    public boolean mouseReleased(final double mx, final double my, final int button) {
        pressed = false;
        return true;
    }
}
