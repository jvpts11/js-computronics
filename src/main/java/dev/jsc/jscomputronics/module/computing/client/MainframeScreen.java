/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.MainframeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Mainframe (and the template for every computer GUI): hardware sections on the left (board, PSU, CPU, RAM, GPU), a divided status/control panel on the right (OFFLINE / READY / POWERED, capacity, queues, buffer, plus the Power and Auto-start buttons).
 */
public class MainframeScreen extends AbstractContainerScreen<MainframeMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int DIVIDER = 0xFF9A9A9A;
    private static final int LABEL = 0xFF404040;
    private static final int PANEL_X = 130;
    private static final int DIVIDER_X = 122;

    private Button powerButton;
    private Button autoButton;

    public MainframeScreen(final MainframeMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 220;
        this.inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        powerButton = addRenderableWidget(Button.builder(Component.literal("Turn On"),
                        b -> sendButton(MainframeMenu.BUTTON_POWER))
                .bounds(leftPos + PANEL_X, topPos + 90, 62, 18).build());
        autoButton = addRenderableWidget(Button.builder(Component.literal("Auto: OFF"),
                        b -> sendButton(MainframeMenu.BUTTON_AUTOSTART))
                .bounds(leftPos + PANEL_X, topPos + 110, 62, 18).build());
    }

    private void sendButton(final int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;

        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 1, BEVEL_LIGHT);
        g.fill(x, y, x + 1, y + imageHeight, BEVEL_LIGHT);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BEVEL_DARK);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BEVEL_DARK);

        // Divider between the hardware area and the status/control panel.
        g.fill(x + DIVIDER_X, y + 16, x + DIVIDER_X + 1, y + 124, DIVIDER);

        slot(g, x + 8, y + 28);   // motherboard
        slot(g, x + 8, y + 72);   // psu
        for (int i = 0; i < 4; i++) {
            slot(g, x + 44 + i * 18, y + 28); // cpu
        }
        for (int i = 0; i < 8; i++) {
            slot(g, x + 44 + (i % 4) * 18, y + 60 + (i / 4) * 18); // ram
        }
        for (int i = 0; i < 4; i++) {
            slot(g, x + 44 + i * 18, y + 110); // gpu
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + 138 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + 196);
        }
    }

    private static void slot(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        super.renderLabels(g, mouseX, mouseY);

        g.drawString(font, "Board", 8, 18, LABEL, false);
        g.drawString(font, "CPU", 44, 18, LABEL, false);
        g.drawString(font, "RAM", 44, 50, LABEL, false);
        g.drawString(font, "PSU", 8, 62, LABEL, false);
        g.drawString(font, "GPU", 44, 100, LABEL, false);

        final String status;
        final int color;
        if (!menu.buildValid()) {
            status = "OFFLINE";
            color = 0xFFA53D3D;
        } else if (menu.isRunning()) {
            status = "POWERED";
            color = 0xFF3DA53D;
        } else {
            status = "READY";
            color = 0xFFB0902A;
        }
        g.drawString(font, status, PANEL_X, 20, color, false);
        g.drawString(font, menu.capacity() + " it/t", PANEL_X, 36, LABEL, false);
        g.drawString(font, menu.parallelQueues() + " queue(s)", PANEL_X, 47, LABEL, false);
        g.drawString(font, menu.ramBuffer() + " buffer", PANEL_X, 58, LABEL, false);

        final int net = menu.networkState();
        final String netLabel = net == 2 ? "Net: CONFLICT" : net == 1 ? "Net: linked" : "Net: --";
        final int netColor = net == 2 ? 0xFFA53D3D : net == 1 ? 0xFF3DA53D : LABEL;
        g.drawString(font, netLabel, PANEL_X, 69, netColor, false);

        // Live Operation dispatch counters: queued, running on virtual threads, done.
        final int running = menu.runningOps();
        g.drawString(font, "Q" + menu.pendingOps() + " R" + running + " D" + menu.completedOps(),
                PANEL_X, 80, running > 0 ? 0xFF3DA53D : LABEL, false);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final boolean auto = menu.isAutoStart();
        powerButton.active = !auto;
        powerButton.setMessage(Component.literal(
                auto ? "Auto" : (menu.isManualOn() ? "Turn Off" : "Turn On")));
        autoButton.setMessage(Component.literal("Auto: " + (auto ? "ON" : "OFF")));
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
