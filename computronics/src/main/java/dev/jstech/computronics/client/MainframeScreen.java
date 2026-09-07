/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.menu.MainframeMenu;
import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.core.network.FailoverRole;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Mainframe: a flat-dark "computer OS" hardware-assembly surface.
 */
public class MainframeScreen extends AbstractComputerScreen<MainframeMenu> {

    private static final int COL_R = 126;
    private static final int COL_R_W = 110;
    private static final int BTN_H = 14;

    // Control row (relative to the GUI top-left): four 54px buttons, 4px gaps, 8px margins.
    private static final int BTN_Y = 162;
    private static final int BTN_W = 54;
    private static final int POWER_X = 8;
    private static final int AUTO_X = 66;
    private static final int FAILOVER_X = 124;

    /** Window-relative centre of the POWER button (client tests press it the way the player does). */
    public static int powerButtonX() {
        return POWER_X + BTN_W / 2;
    }

    public static int powerButtonY() {
        return BTN_Y + BTN_H / 2;
    }

    public MainframeScreen(final MainframeMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 244;
        this.imageHeight = 262;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JsTechTheme.window(g, x, y, imageWidth, imageHeight);
        JsTechTheme.headerBar(g, x + 6, y + 6, 232);
        JsTechTheme.vLine(g, x + COL_R - 5, y + 24, 134);

        // Hardware cells, only up to the count the installed board exposes.
        JsTechTheme.slot(g, x + 8, y + 40);   // motherboard
        JsTechTheme.slot(g, x + 8, y + 73);   // psu
        /*
         * The board-derived counts are already clamped to the chassis bays in the BlockEntity, so the
         * screen draws exactly what the menu exposes, one source of truth, no duplicated cap literal.
         */
        final int cpu = menu.boardCpuSlots();
        final int ram = menu.boardRamSlots();
        final int gpu = menu.boardPcieSlots();
        final int disk = menu.boardDiskSlots();
        for (int i = 0; i < cpu; i++) {
            JsTechTheme.slot(g, x + 44 + i * 18, y + 40);
        }
        for (int i = 0; i < ram; i++) {
            JsTechTheme.slot(g, x + 44 + (i % 4) * 18, y + 73 + (i / 4) * 18);
        }
        for (int i = 0; i < gpu; i++) {
            JsTechTheme.slot(g, x + 44 + (i % 3) * 18, y + 124 + (i / 3) * 18);
        }
        for (int i = 0; i < disk; i++) {
            JsTechTheme.slot(g, x + 8 + (i % 2) * 18, y + 124 + (i / 2) * 18);
        }

        // Right spec column.
        JsTechTheme.panel(g, x + COL_R, y + 27, COL_R_W, 22);   // CAPACITY
        JsTechTheme.panel(g, x + COL_R, y + 52, COL_R_W, 18);   // QUEUES
        JsTechTheme.panel(g, x + COL_R, y + 73, COL_R_W, 18);   // RAM BUFFER
        JsTechTheme.panel(g, x + COL_R, y + 108, COL_R_W, 42);  // OPERATIONS

        final boolean auto = menu.isAutoStart();
        JsTechTheme.button(g, x + POWER_X, y + BTN_Y, BTN_W, BTN_H,
                !auto && hover(mouseX, mouseY, POWER_X, BTN_Y, BTN_W, BTN_H));
        JsTechTheme.button(g, x + AUTO_X, y + BTN_Y, BTN_W, BTN_H, hover(mouseX, mouseY, AUTO_X, BTN_Y, BTN_W, BTN_H));
        JsTechTheme.button(g, x + FAILOVER_X, y + BTN_Y, BTN_W, BTN_H, hover(mouseX, mouseY, FAILOVER_X, BTN_Y, BTN_W, BTN_H));

        // Player inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JsTechTheme.slot(g, x + 8 + col * 18, y + 182 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JsTechTheme.slot(g, x + 8 + col * 18, y + 240);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JsTechTheme.text(g, font, "MAINFRAME", 12, 11, JsTechTheme.text());
        final String status;
        final int statusColor;
        if (menu.networkState() == MainframeBlockEntity.NET_STATE_CONFLICT) {
            status = "CONFLICT";
            statusColor = JsTechTheme.red();
        } else if (!menu.buildValid()) {
            status = "OFFLINE";
            statusColor = JsTechTheme.red();
        } else if (menu.isRunning() && menu.failoverRole() == FailoverRole.PASSIVE.ordinal()) {
            status = "STANDBY"; // a Passive Failover member: powered and synced, not orchestrating
            statusColor = JsTechTheme.amber();
        } else if (menu.isRunning()) {
            status = "ONLINE";
            statusColor = JsTechTheme.green();
        } else {
            status = "READY";
            statusColor = JsTechTheme.amber();
        }
        final int pillX = 232 - font.width(status);
        JsTechTheme.text(g, font, status, pillX, 11, statusColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, statusColor);

        // Hardware group labels (no counters, the drawn cells show installed vs available).
        JsTechTheme.text(g, font, "BOARD", 8, 27, menu.hasBoard() ? JsTechTheme.accent() : JsTechTheme.dim());
        JsTechTheme.text(g, font, "CPU", 44, 27, JsTechTheme.dim());
        JsTechTheme.text(g, font, "PSU", 8, 60, JsTechTheme.dim());
        g.fill(30, 61, 34, 65, psuColor());
        JsTechTheme.text(g, font, "RAM", 44, 60, JsTechTheme.dim());
        JsTechTheme.text(g, font, "DISK", 8, 111, JsTechTheme.dim());
        JsTechTheme.text(g, font, "GPU", 44, 111, JsTechTheme.dim());

        // Right column: spec tiles.
        JsTechTheme.tileText(g, font, COL_R, 27, "CAPACITY", JsTechTheme.fmt(menu.capacity()), "it/t", JsTechTheme.text());
        JsTechTheme.tileText(g, font, COL_R, 52, "QUEUES", String.valueOf(menu.parallelQueues()), "", JsTechTheme.text());
        JsTechTheme.tileText(g, font, COL_R, 73, "RAM BUFFER", JsTechTheme.fmt(menu.ramBuffer()), "it", JsTechTheme.text());

        JsTechTheme.text(g, font, "NETWORK", COL_R, 96, JsTechTheme.dim());
        final int net = menu.networkState();
        final String netStr = net == MainframeBlockEntity.NET_STATE_CONFLICT ? "CONFLICT"
                : net == MainframeBlockEntity.NET_STATE_LINKED ? "LINKED" : "--";
        final int netColor = net == MainframeBlockEntity.NET_STATE_CONFLICT ? JsTechTheme.red()
                : net == MainframeBlockEntity.NET_STATE_LINKED ? JsTechTheme.green() : JsTechTheme.dim();
        JsTechTheme.textRight(g, font, netStr, COL_R + COL_R_W, 96, netColor);

        // Operations dispatch: one row per metric (label left, value right).
        final int running = menu.runningOps();
        opRow(g, "QUEUED", String.valueOf(menu.pendingOps()), 113, JsTechTheme.text());
        opRow(g, "RUNNING", String.valueOf(running), 124, running > 0 ? JsTechTheme.green() : JsTechTheme.text());
        opRow(g, "DONE", JsTechTheme.fmt(menu.completedOps()), 135, JsTechTheme.text());

        // Control row captions.
        final boolean auto = menu.isAutoStart();
        final String powerCap = auto ? "AUTO" : (menu.isManualOn() ? "TURN OFF" : "TURN ON");
        JsTechTheme.textCenter(g, font, powerCap, POWER_X + BTN_W / 2, BTN_Y + 4, auto ? JsTechTheme.dim() : JsTechTheme.accent());
        JsTechTheme.textCenter(g, font, "AUTO " + (auto ? "ON" : "OFF"), AUTO_X + BTN_W / 2, BTN_Y + 4,
                auto ? JsTechTheme.accent() : JsTechTheme.dim());
        final boolean failover = menu.failoverEnabled();
        JsTechTheme.textCenter(g, font, "FAIL " + (failover ? "ON" : "OFF"), FAILOVER_X + BTN_W / 2, BTN_Y + 4,
                failover ? JsTechTheme.accent() : JsTechTheme.dim());
    }

    private void opRow(final GuiGraphics g, final String key, final String value, final int y, final int valueColor) {
        JsTechTheme.text(g, font, key, COL_R + 4, y, JsTechTheme.dim());
        JsTechTheme.textRight(g, font, value, COL_R + COL_R_W - 4, y, valueColor);
    }

    private int psuColor() {
        if (!menu.hasPsu()) {
            return JsTechTheme.dim();
        }
        return menu.buildValid() ? JsTechTheme.green() : JsTechTheme.amber();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            if (!menu.isAutoStart() && hover((int) mouseX, (int) mouseY, POWER_X, BTN_Y, BTN_W, BTN_H)) {
                sendButton(MainframeMenu.BUTTON_POWER);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, AUTO_X, BTN_Y, BTN_W, BTN_H)) {
                sendButton(MainframeMenu.BUTTON_AUTOSTART);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, FAILOVER_X, BTN_Y, BTN_W, BTN_H)) {
                sendButton(MainframeMenu.BUTTON_FAILOVER);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected HardwareEra screenEra() {
        return menu.hardwareEra();
    }
}
