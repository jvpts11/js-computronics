/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A {@link Button} that paints itself through {@link JscOsTheme} instead of the vanilla 9-slice texture, so every
 * clickable control matches the active era's skin. Behaviour (press handling, narration, visibility, focus) is the
 * vanilla button's; only the look changes. Screens use this everywhere a button is needed rather than the default
 * {@code Button.builder(...)}.
 */
public class ThemeButton extends Button {

    public ThemeButton(final int x, final int y, final int width, final int height,
                       final Component message, final OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final boolean hovered = this.isHovered();
        JscOsTheme.button(g, getX(), getY(), getWidth(), getHeight(), hovered && this.active);
        final int color = !this.active ? JscOsTheme.dim()
                : hovered ? JscOsTheme.text() : JscOsTheme.accent();
        JscOsTheme.textCenter(g, Minecraft.getInstance().font, getMessage().getString(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
    }
}
