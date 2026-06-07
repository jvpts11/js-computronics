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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Personal Computer: a flat-dark "computer OS" hardware-assembly surface, the same skin as the Mainframe.
 */
public class PersonalComputerScreen extends AbstractContainerScreen<PersonalComputerMenu> {

    private static final int COL_R = 126;
    private static final int COL_R_W = 110;
    private static final int BTN_H = 14;

    private static final int POWER_X = COL_R;
    private static final int POWER_Y = 98;
    private static final int AUTO_X = COL_R;
    private static final int AUTO_Y = 116;

    public PersonalComputerScreen(final PersonalComputerMenu menu, final Inventory inventory,
                                  final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 244;
        this.imageHeight = 218;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, 232);
        JscOsTheme.vLine(g, x + COL_R - 5, y + 24, 108);

        JscOsTheme.slot(g, x + 8, y + 40);  // motherboard
        JscOsTheme.slot(g, x + 8, y + 73);  // psu
        if (menu.boardCpuSlots() > 0) {
            JscOsTheme.slot(g, x + 44, y + 40);
        }
        final int ram = Math.min(menu.boardRamSlots(), 4);
        final int gpu = Math.min(menu.boardPcieSlots(), 4);
        final int disk = Math.min(menu.boardDiskSlots(), 2);
        for (int i = 0; i < ram; i++) {
            JscOsTheme.slot(g, x + 44 + i * 18, y + 73);
        }
        for (int i = 0; i < gpu; i++) {
            JscOsTheme.slot(g, x + 44 + i * 18, y + 106);
        }
        for (int i = 0; i < disk; i++) {
            JscOsTheme.slot(g, x + 8 + i * 18, y + 106);
        }

        JscOsTheme.panel(g, x + COL_R, y + 27, COL_R_W, 22);  // CAPACITY
        JscOsTheme.panel(g, x + COL_R, y + 52, COL_R_W, 18);  // RAM BUFFER

        final boolean auto = menu.isAutoStart();
        JscOsTheme.button(g, x + POWER_X, y + POWER_Y, COL_R_W, BTN_H,
                !auto && hover(mouseX, mouseY, POWER_X, POWER_Y, COL_R_W, BTN_H));
        JscOsTheme.button(g, x + AUTO_X, y + AUTO_Y, COL_R_W, BTN_H, hover(mouseX, mouseY, AUTO_X, AUTO_Y, COL_R_W, BTN_H));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + 8 + col * 18, y + 138 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + 8 + col * 18, y + 196);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "PERSONAL COMPUTER", 12, 11, JscOsTheme.TEXT);
        final String status;
        final int statusColor;
        if (!menu.buildValid()) {
            status = "OFFLINE";
            statusColor = JscOsTheme.RED;
        } else if (menu.isRunning()) {
            status = "ONLINE";
            statusColor = JscOsTheme.GREEN;
        } else {
            status = "READY";
            statusColor = JscOsTheme.AMBER;
        }
        final int pillX = 232 - font.width(status);
        JscOsTheme.text(g, font, status, pillX, 11, statusColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, statusColor);

        JscOsTheme.text(g, font, "BOARD", 8, 27, menu.hasBoard() ? JscOsTheme.ACCENT : JscOsTheme.DIM);
        JscOsTheme.text(g, font, "CPU", 44, 27, JscOsTheme.DIM);
        JscOsTheme.text(g, font, "PSU", 8, 60, JscOsTheme.DIM);
        g.fill(30, 61, 34, 65, psuColor());
        JscOsTheme.text(g, font, "RAM", 44, 60, JscOsTheme.DIM);
        JscOsTheme.text(g, font, "DISK", 8, 93, JscOsTheme.DIM);
        JscOsTheme.text(g, font, "GPU", 44, 93, JscOsTheme.DIM);

        JscOsTheme.tileText(g, font, COL_R, 27, "CAPACITY", JscOsTheme.fmt(menu.capacity()), "it/t", JscOsTheme.TEXT);
        JscOsTheme.tileText(g, font, COL_R, 52, "RAM BUFFER", JscOsTheme.fmt(menu.ramBuffer()), "it", JscOsTheme.TEXT);

        JscOsTheme.text(g, font, "NETWORK", COL_R, 74, JscOsTheme.DIM);
        if (menu.isOnNetwork()) {
            JscOsTheme.textRight(g, font, "LINKED", COL_R + COL_R_W, 74, JscOsTheme.GREEN);
            final int n = menu.networkServerCount();
            JscOsTheme.textRight(g, font, n + (n == 1 ? " server" : " servers"), COL_R + COL_R_W, 85, JscOsTheme.DIM);
        } else {
            JscOsTheme.textRight(g, font, "--", COL_R + COL_R_W, 74, JscOsTheme.DIM);
        }

        final boolean auto = menu.isAutoStart();
        final String powerCap = auto ? "AUTO" : (menu.isRunning() ? "TURN OFF" : "TURN ON");
        JscOsTheme.textCenter(g, font, powerCap, POWER_X + COL_R_W / 2, POWER_Y + 4, auto ? JscOsTheme.DIM : JscOsTheme.ACCENT);
        JscOsTheme.textCenter(g, font, "AUTO: " + (auto ? "ON" : "OFF"), AUTO_X + COL_R_W / 2, AUTO_Y + 4,
                auto ? JscOsTheme.ACCENT : JscOsTheme.DIM);
    }

    private int psuColor() {
        if (!menu.hasPsu()) {
            return JscOsTheme.DIM;
        }
        return menu.buildValid() ? JscOsTheme.GREEN : JscOsTheme.AMBER;
    }

    private boolean hover(final int mouseX, final int mouseY, final int rx, final int ry, final int w, final int h) {
        final int mx = mouseX - leftPos;
        final int my = mouseY - topPos;
        return mx >= rx && mx < rx + w && my >= ry && my < ry + h;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            if (!menu.isAutoStart() && hover((int) mouseX, (int) mouseY, POWER_X, POWER_Y, COL_R_W, BTN_H)) {
                sendButton(PersonalComputerMenu.BUTTON_POWER);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, AUTO_X, AUTO_Y, COL_R_W, BTN_H)) {
                sendButton(PersonalComputerMenu.BUTTON_AUTOSTART);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendButton(final int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
