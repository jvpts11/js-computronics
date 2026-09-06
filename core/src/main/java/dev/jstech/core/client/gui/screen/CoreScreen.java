/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base screen for mod GUIs that are NOT bound to a container menu.
 */
public abstract class CoreScreen extends Screen {

    protected CoreScreen(final Component title) {
        super(title);
    }

    @Override
    public void render(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderContent(graphics, mouseX, mouseY, partialTick);
    }

    protected abstract void renderContent(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public boolean isPauseScreen() {
        // Mod screens do not pause singleplayer — a running computer or
        // machine keeps ticking while its GUI is open.
        return false;
    }
}