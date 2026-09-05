/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Base screen for mod GUIs that ARE bound to a container menu (slots / inventory): machines with input/output slots, computers with drive bays, etc. Counterpart to {@link JscScreen} (which is menu-less).
 */
public abstract class JscContainerScreen<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {

    protected JscContainerScreen(
            final T menu,
            final Inventory playerInventory,
            final Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void render(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        // Vanilla AbstractContainerScreen renders tooltips for hovered
        // slots when this is called after super.
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(
            final GuiGraphics graphics,
            final float partialTick,
            final int mouseX,
            final int mouseY) {
        graphics.fill(
                leftPos, topPos,
                leftPos + imageWidth, topPos + imageHeight,
                0xF0202020);
    }
}