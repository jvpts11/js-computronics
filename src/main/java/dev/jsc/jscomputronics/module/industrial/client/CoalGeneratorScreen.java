/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial.client;

import dev.jsc.jscomputronics.module.industrial.menu.CoalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Coal Generator: a fuel slot, a burn indicator and an FE gauge showing the energy produced.
 */
public class CoalGeneratorScreen extends AbstractContainerScreen<CoalGeneratorMenu> {

    private static final int FLAME_EMPTY = 0xFF555555;
    private static final int FLAME_FULL = 0xFFFF9020;
    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 16;
    private static final int ENERGY_W = 10;
    private static final int ENERGY_H = 52;

    public CoalGeneratorScreen(final CoalGeneratorMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;

        MachineScreenSupport.drawPanel(g, x, y, imageWidth, imageHeight);
        MachineScreenSupport.drawPlayerInventory(g, x, y);
        MachineScreenSupport.drawSlot(g, x + 80, y + 53);

        final int flameX = x + 81;
        final int flameY = y + 38;
        final int flameW = 14;
        final int flameH = 14;
        g.fill(flameX, flameY, flameX + flameW, flameY + flameH, FLAME_EMPTY);
        final int lit = menu.getBurnScaled();
        if (lit > 0) {
            g.fill(flameX, flameY + (flameH - lit), flameX + flameW, flameY + flameH, FLAME_FULL);
        }

        MachineScreenSupport.drawEnergyBar(g, x + ENERGY_X, y + ENERGY_Y, ENERGY_W, ENERGY_H,
                menu.getEnergy(), menu.getMaxEnergy());
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        // AbstractContainerScreen#render already draws the dimmed/blurred
        // background; calling renderBackground here too would darken it twice.
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            g.renderTooltip(font,
                    Component.literal(menu.getEnergy() + " / " + menu.getMaxEnergy() + " FE"),
                    mouseX, mouseY);
        }
    }
}
