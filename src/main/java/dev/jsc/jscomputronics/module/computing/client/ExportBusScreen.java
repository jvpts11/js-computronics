/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.block.part.ExportBusPart;
import dev.jsc.jscomputronics.module.computing.menu.ExportBusMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Configuration screen for the Export Bus, in the shared flat-dark "computer OS" theme: the ghost filter slot, the destination min/max stock steppers (shift for ×16), and the continuous/redstone mode toggle, over the player inventory.
 */
public class ExportBusScreen extends AbstractComputerScreen<ExportBusMenu> {

    private static final int STEP = 12;
    private static final int MIN_Y = 26;
    private static final int MAX_Y = 44;
    private static final int MINUS_X = 66;
    private static final int PLUS_X = 150;
    private static final int MODE_X = 66;
    private static final int MODE_Y = 60;
    private static final int MODE_W = 96;

    public ExportBusScreen(final ExportBusMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 171;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, 164);

        JscOsTheme.slot(g, x + 12, y + 30); // ghost filter slot (matches the menu slot position)

        // Min / max steppers.
        stepperBg(g, x, y, MIN_Y, mouseX, mouseY);
        stepperBg(g, x, y, MAX_Y, mouseX, mouseY);
        // Mode toggle.
        JscOsTheme.button(g, x + MODE_X, y + MODE_Y, MODE_W, 14, hover(mouseX, mouseY, MODE_X, MODE_Y, MODE_W, 14));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + 8 + col * 18, y + 89 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + 8 + col * 18, y + 147);
        }
    }

    private void stepperBg(final GuiGraphics g, final int x, final int y, final int row,
                           final int mouseX, final int mouseY) {
        JscOsTheme.button(g, x + MINUS_X, y + row, STEP, STEP, hover(mouseX, mouseY, MINUS_X, row, STEP, STEP));
        g.fill(x + MINUS_X + STEP + 2, y + row, x + PLUS_X - 2, y + row + STEP, JscOsTheme.track());
        JscOsTheme.hLine(g, x + MINUS_X + STEP + 2, y + row, PLUS_X - MINUS_X - STEP - 4);
        JscOsTheme.button(g, x + PLUS_X, y + row, STEP, STEP, hover(mouseX, mouseY, PLUS_X, row, STEP, STEP));
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "EXPORT BUS", 12, 11, JscOsTheme.text());
        final boolean linked = menu.linked();
        final String pill = linked ? "LINKED" : "OFFLINE";
        final int pillColor = linked ? JscOsTheme.green() : JscOsTheme.red();
        final int pillX = 164 - font.width(pill);
        JscOsTheme.text(g, font, pill, pillX, 11, pillColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, pillColor);

        // Min / max steppers: label + centered value + "-"/"+".
        JscOsTheme.text(g, font, "MIN", 40, MIN_Y + 3, JscOsTheme.dim());
        JscOsTheme.text(g, font, "MAX", 40, MAX_Y + 3, JscOsTheme.dim());
        JscOsTheme.textCenter(g, font, "-", MINUS_X + STEP / 2, MIN_Y + 3, JscOsTheme.accent());
        JscOsTheme.textCenter(g, font, "-", MINUS_X + STEP / 2, MAX_Y + 3, JscOsTheme.accent());
        JscOsTheme.textCenter(g, font, "+", PLUS_X + STEP / 2, MIN_Y + 3, JscOsTheme.accent());
        JscOsTheme.textCenter(g, font, "+", PLUS_X + STEP / 2, MAX_Y + 3, JscOsTheme.accent());
        final int mid = (MINUS_X + STEP + PLUS_X) / 2;
        JscOsTheme.textCenter(g, font, String.valueOf(menu.min()), mid, MIN_Y + 3, JscOsTheme.text());
        JscOsTheme.textCenter(g, font, menu.max() <= 0 ? "any" : String.valueOf(menu.max()), mid, MAX_Y + 3, JscOsTheme.text());

        // Mode toggle.
        JscOsTheme.text(g, font, "MODE", 40, MODE_Y + 4, JscOsTheme.dim());
        final String modeText = menu.mode() == ExportBusPart.MODE_CONTINUOUS ? "CONTINUOUS" : "ON DEMAND";
        JscOsTheme.textCenter(g, font, modeText, MODE_X + MODE_W / 2, MODE_Y + 4, JscOsTheme.accent());

        JscOsTheme.text(g, font, "INVENTORY", 8, 80, JscOsTheme.dim());
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            final boolean shift = hasShiftDown();
            if (hover((int) mouseX, (int) mouseY, MINUS_X, MIN_Y, STEP, STEP)) {
                send(shift ? ExportBusMenu.BTN_MIN_DOWN16 : ExportBusMenu.BTN_MIN_DOWN1);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, PLUS_X, MIN_Y, STEP, STEP)) {
                send(shift ? ExportBusMenu.BTN_MIN_UP16 : ExportBusMenu.BTN_MIN_UP1);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, MINUS_X, MAX_Y, STEP, STEP)) {
                send(shift ? ExportBusMenu.BTN_MAX_DOWN16 : ExportBusMenu.BTN_MAX_DOWN1);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, PLUS_X, MAX_Y, STEP, STEP)) {
                send(shift ? ExportBusMenu.BTN_MAX_UP16 : ExportBusMenu.BTN_MAX_UP1);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, MODE_X, MODE_Y, MODE_W, 14)) {
                send(ExportBusMenu.BTN_MODE);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(final int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        // super.render binds the era skin, draws the background and widgets, and renders the slot tooltip.
        super.render(g, mouseX, mouseY, partialTick);
        // Hint on the empty ghost filter slot (a held item sets the filter, not consumed).
        if (menu.filterStack().isEmpty() && hover(mouseX, mouseY, 12, 30, 16, 16)) {
            g.renderTooltip(font, Component.literal("Click an item to set the export filter"), mouseX, mouseY);
        }
    }
}
