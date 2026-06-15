/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial.client;

import dev.jsc.jscomputronics.client.gui.screen.AbstractMachineScreen;
import dev.jsc.jscomputronics.module.industrial.menu.CompressorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Compressor: an input slot, a pressing progress bar and an FE gauge.
 */
public class CompressorScreen extends AbstractMachineScreen<CompressorMenu> {

    private static final int PROGRESS_FILL = 0xFF4488FF;
    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 16;
    private static final int ENERGY_W = 10;
    private static final int ENERGY_H = 52;

    public CompressorScreen(final CompressorMenu menu, final Inventory inventory,
                            final Component title) {
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
        MachineScreenSupport.drawSlot(g, x + 56, y + 35);
        MachineScreenSupport.drawSlot(g, x + 116, y + 35);
        MachineScreenSupport.drawProgressBar(g, x + 79, y + 38, 24, 8,
                menu.getProgress(), menu.getMaxProgress(), PROGRESS_FILL);
        MachineScreenSupport.drawEnergyBar(g, x + ENERGY_X, y + ENERGY_Y, ENERGY_W, ENERGY_H,
                menu.getEnergy(), menu.getMaxEnergy());
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (hover(mouseX, mouseY, ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H)) {
            g.renderTooltip(font,
                    Component.literal(menu.getEnergy() + " / " + menu.getMaxEnergy() + " FE"),
                    mouseX, mouseY);
        }
    }
}
