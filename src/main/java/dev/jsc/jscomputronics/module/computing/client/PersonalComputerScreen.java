/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Personal Computer.
 */
public class PersonalComputerScreen extends AbstractContainerScreen<PersonalComputerMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int DIVIDER = 0xFF9A9A9A;
    private static final int LABEL = 0xFF404040;
    private static final int VIEW_FILL = 0xFFB2B2B2;
    private static final int INFO_X = 126;
    private static final int DIVIDER_X = 120;

    private Button localTab;
    private Button networkTab;
    private Button powerButton;
    private Button autoButton;

    public PersonalComputerScreen(final PersonalComputerMenu menu, final Inventory inventory,
                                  final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 256;
        this.inventoryLabelY = 162;
    }

    @Override
    protected void init() {
        super.init();
        localTab = addRenderableWidget(Button.builder(Component.literal("Local"),
                        b -> selectTab(PersonalComputerMenu.TAB_LOCAL))
                .bounds(leftPos + 6, topPos + 2, 42, 14).build());
        networkTab = addRenderableWidget(Button.builder(Component.literal("Network"),
                        b -> selectTab(PersonalComputerMenu.TAB_NETWORK))
                .bounds(leftPos + 50, topPos + 2, 54, 14).build());
        powerButton = addRenderableWidget(Button.builder(Component.literal("Turn On"),
                        b -> sendButton(PersonalComputerMenu.BUTTON_POWER))
                .bounds(leftPos + INFO_X, topPos + 78, 66, 16).build());
        autoButton = addRenderableWidget(Button.builder(Component.literal("Auto: OFF"),
                        b -> sendButton(PersonalComputerMenu.BUTTON_AUTOSTART))
                .bounds(leftPos + INFO_X, topPos + 96, 66, 16).build());
    }

    private void selectTab(final int tab) {
        menu.setActiveTab(tab);                                   // client-side, immediate
        sendButton(PersonalComputerMenu.BUTTON_TAB_BASE + tab);   // sync the server
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
        g.fill(x, y + 18, x + imageWidth, y + 19, DIVIDER); // under the tab strip

        if (menu.activeTab() == PersonalComputerMenu.TAB_LOCAL) {
            g.fill(x + DIVIDER_X, y + 20, x + DIVIDER_X + 1, y + 112, DIVIDER); // hardware | status

            slot(g, x + 8, y + 32);  // motherboard (always)
            slot(g, x + 8, y + 64);  // psu (always)
            if (menu.boardCpuSlots() > 0) {
                slot(g, x + 44, y + 32); // cpu
            }
            final int ram = Math.min(menu.boardRamSlots(), 4);
            final int gpu = Math.min(menu.boardPcieSlots(), 4);
            for (int i = 0; i < ram; i++) {
                slot(g, x + 44 + i * 18, y + 64);
            }
            for (int i = 0; i < gpu; i++) {
                slot(g, x + 44 + i * 18, y + 96);
            }
            final int disk = Math.min(menu.boardDiskSlots(), 2);
            for (int i = 0; i < disk; i++) {
                slot(g, x + 8 + i * 18, y + 96);
            }
            g.fill(x + 6, y + 120, x + 170, y + 121, DIVIDER); // above storage
            for (int i = 0; i < 18; i++) {
                slot(g, x + 8 + (i % 9) * 18, y + 124 + (i / 9) * 18);
            }
        } else {
            g.fill(x + 8, y + 24, x + 192, y + 156, VIEW_FILL); // network view area
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + 172 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + 232);
        }
    }

    private static void slot(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, LABEL, false);

        if (menu.activeTab() == PersonalComputerMenu.TAB_LOCAL) {
            g.drawString(font, "Board", 8, 22, LABEL, false);
            g.drawString(font, "PSU", 8, 54, LABEL, false);
            g.drawString(font, "CPU", 44, 22, LABEL, false);
            g.drawString(font, "RAM", 44, 54, LABEL, false);
            g.drawString(font, "GPU", 44, 86, LABEL, false);
            g.drawString(font, "Disk", 8, 86, LABEL, false);
            g.drawString(font, "Storage", 8, 113, LABEL, false);

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
            g.drawString(font, status, INFO_X, 28, color, false);
            g.drawString(font, menu.capacity() + " it/t", INFO_X, 40, LABEL, false);
            g.drawString(font, menu.ramBuffer() + " buf", INFO_X, 52, LABEL, false);
            final boolean net = menu.isOnNetwork();
            g.drawString(font, net ? "Net: linked" : "Net: --", INFO_X, 64, net ? 0xFF3DA53D : LABEL, false);
        } else {
            g.drawString(font, "Network", 12, 32, 0xFF11507E, false);
            g.drawString(font, "Connect to a network", 12, 50, LABEL, false);
            g.drawString(font, "with Servers to browse", 12, 62, LABEL, false);
            g.drawString(font, "and SELECT items.", 12, 74, LABEL, false);
            final boolean net = menu.isOnNetwork();
            g.drawString(font, net ? "On network: yes" : "On network: no", 12, 98,
                    net ? 0xFF3DA53D : 0xFFA53D3D, false);
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final boolean local = menu.activeTab() == PersonalComputerMenu.TAB_LOCAL;
        localTab.active = !local;
        networkTab.active = local;

        final boolean auto = menu.isAutoStart();
        powerButton.visible = local;
        autoButton.visible = local;
        powerButton.active = local && !auto;
        powerButton.setMessage(Component.literal(
                auto ? "Auto" : (menu.isRunning() ? "Turn Off" : "Turn On")));
        autoButton.setMessage(Component.literal("Auto: " + (auto ? "ON" : "OFF")));

        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
