/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.common.network.FailoverRole;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.MainframeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Mainframe: a flat-dark "computer OS" hardware-assembly surface.
 */
public class MainframeScreen extends AbstractContainerScreen<MainframeMenu> {

    private static final int COL_R = 126;
    private static final int COL_R_W = 110;
    private static final int BTN_H = 14;

    // Control row (relative to the GUI top-left): four 54px buttons, 4px gaps, 8px margins.
    private static final int BTN_Y = 162;
    private static final int BTN_W = 54;
    private static final int POWER_X = 8;
    private static final int AUTO_X = 66;
    private static final int FAILOVER_X = 124;
    private static final int NODES_X = 182;

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
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, 232);
        JscOsTheme.vLine(g, x + COL_R - 5, y + 24, 134);

        // Hardware cells, only up to the count the installed board exposes.
        JscOsTheme.slot(g, x + 8, y + 40);   // motherboard
        JscOsTheme.slot(g, x + 8, y + 73);   // psu
        // The board-derived counts are already clamped to the chassis bays in the BlockEntity, so the
        // screen draws exactly what the menu exposes — one source of truth, no duplicated cap literal.
        final int cpu = menu.boardCpuSlots();
        final int ram = menu.boardRamSlots();
        final int gpu = menu.boardPcieSlots();
        final int disk = menu.boardDiskSlots();
        for (int i = 0; i < cpu; i++) {
            JscOsTheme.slot(g, x + 44 + i * 18, y + 40);
        }
        for (int i = 0; i < ram; i++) {
            JscOsTheme.slot(g, x + 44 + (i % 4) * 18, y + 73 + (i / 4) * 18);
        }
        for (int i = 0; i < gpu; i++) {
            JscOsTheme.slot(g, x + 44 + (i % 3) * 18, y + 124 + (i / 3) * 18);
        }
        for (int i = 0; i < disk; i++) {
            JscOsTheme.slot(g, x + 8 + (i % 2) * 18, y + 124 + (i / 2) * 18);
        }

        // Right spec column.
        JscOsTheme.panel(g, x + COL_R, y + 27, COL_R_W, 22);   // CAPACITY
        JscOsTheme.panel(g, x + COL_R, y + 52, COL_R_W, 18);   // QUEUES
        JscOsTheme.panel(g, x + COL_R, y + 73, COL_R_W, 18);   // RAM BUFFER
        JscOsTheme.panel(g, x + COL_R, y + 108, COL_R_W, 42);  // OPERATIONS

        final boolean auto = menu.isAutoStart();
        JscOsTheme.button(g, x + POWER_X, y + BTN_Y, BTN_W, BTN_H,
                !auto && hover(mouseX, mouseY, POWER_X, BTN_Y, BTN_W, BTN_H));
        JscOsTheme.button(g, x + AUTO_X, y + BTN_Y, BTN_W, BTN_H, hover(mouseX, mouseY, AUTO_X, BTN_Y, BTN_W, BTN_H));
        JscOsTheme.button(g, x + FAILOVER_X, y + BTN_Y, BTN_W, BTN_H, hover(mouseX, mouseY, FAILOVER_X, BTN_Y, BTN_W, BTN_H));
        JscOsTheme.button(g, x + NODES_X, y + BTN_Y, BTN_W, BTN_H, hover(mouseX, mouseY, NODES_X, BTN_Y, BTN_W, BTN_H));

        // Player inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + 8 + col * 18, y + 182 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + 8 + col * 18, y + 240);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "MAINFRAME", 12, 11, JscOsTheme.TEXT);
        final String status;
        final int statusColor;
        if (menu.networkState() == MainframeBlockEntity.NET_STATE_CONFLICT) {
            status = "CONFLICT";
            statusColor = JscOsTheme.RED;
        } else if (!menu.buildValid()) {
            status = "OFFLINE";
            statusColor = JscOsTheme.RED;
        } else if (menu.isRunning() && menu.failoverRole() == FailoverRole.PASSIVE.ordinal()) {
            status = "STANDBY"; // a Passive Failover member: powered and synced, not orchestrating
            statusColor = JscOsTheme.AMBER;
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

        // Hardware group labels (no counters — the drawn cells show installed vs available).
        JscOsTheme.text(g, font, "BOARD", 8, 27, menu.hasBoard() ? JscOsTheme.ACCENT : JscOsTheme.DIM);
        JscOsTheme.text(g, font, "CPU", 44, 27, JscOsTheme.DIM);
        JscOsTheme.text(g, font, "PSU", 8, 60, JscOsTheme.DIM);
        g.fill(30, 61, 34, 65, psuColor());
        JscOsTheme.text(g, font, "RAM", 44, 60, JscOsTheme.DIM);
        JscOsTheme.text(g, font, "DISK", 8, 111, JscOsTheme.DIM);
        JscOsTheme.text(g, font, "GPU", 44, 111, JscOsTheme.DIM);

        // Right column: spec tiles.
        JscOsTheme.tileText(g, font, COL_R, 27, "CAPACITY", JscOsTheme.fmt(menu.capacity()), "it/t", JscOsTheme.TEXT);
        JscOsTheme.tileText(g, font, COL_R, 52, "QUEUES", String.valueOf(menu.parallelQueues()), "", JscOsTheme.TEXT);
        JscOsTheme.tileText(g, font, COL_R, 73, "RAM BUFFER", JscOsTheme.fmt(menu.ramBuffer()), "it", JscOsTheme.TEXT);

        JscOsTheme.text(g, font, "NETWORK", COL_R, 96, JscOsTheme.DIM);
        final int net = menu.networkState();
        final String netStr = net == MainframeBlockEntity.NET_STATE_CONFLICT ? "CONFLICT"
                : net == MainframeBlockEntity.NET_STATE_LINKED ? "LINKED" : "--";
        final int netColor = net == MainframeBlockEntity.NET_STATE_CONFLICT ? JscOsTheme.RED
                : net == MainframeBlockEntity.NET_STATE_LINKED ? JscOsTheme.GREEN : JscOsTheme.DIM;
        JscOsTheme.textRight(g, font, netStr, COL_R + COL_R_W, 96, netColor);

        // Operations dispatch — one row per metric (label left, value right).
        final int running = menu.runningOps();
        opRow(g, "QUEUED", String.valueOf(menu.pendingOps()), 113, JscOsTheme.TEXT);
        opRow(g, "RUNNING", String.valueOf(running), 124, running > 0 ? JscOsTheme.GREEN : JscOsTheme.TEXT);
        opRow(g, "DONE", JscOsTheme.fmt(menu.completedOps()), 135, JscOsTheme.TEXT);

        // Control row captions.
        final boolean auto = menu.isAutoStart();
        final String powerCap = auto ? "AUTO" : (menu.isManualOn() ? "TURN OFF" : "TURN ON");
        JscOsTheme.textCenter(g, font, powerCap, POWER_X + BTN_W / 2, BTN_Y + 4, auto ? JscOsTheme.DIM : JscOsTheme.ACCENT);
        JscOsTheme.textCenter(g, font, "AUTO " + (auto ? "ON" : "OFF"), AUTO_X + BTN_W / 2, BTN_Y + 4,
                auto ? JscOsTheme.ACCENT : JscOsTheme.DIM);
        final boolean failover = menu.failoverEnabled();
        JscOsTheme.textCenter(g, font, "FAIL " + (failover ? "ON" : "OFF"), FAILOVER_X + BTN_W / 2, BTN_Y + 4,
                failover ? JscOsTheme.ACCENT : JscOsTheme.DIM);
        JscOsTheme.textCenter(g, font, "NODES", NODES_X + BTN_W / 2, BTN_Y + 4, JscOsTheme.ACCENT);
    }

    private void opRow(final GuiGraphics g, final String key, final String value, final int y, final int valueColor) {
        JscOsTheme.text(g, font, key, COL_R + 4, y, JscOsTheme.DIM);
        JscOsTheme.textRight(g, font, value, COL_R + COL_R_W - 4, y, valueColor);
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
            if (hover((int) mouseX, (int) mouseY, NODES_X, BTN_Y, BTN_W, BTN_H)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new dev.jsc.jscomputronics.module.computing.operation.payload.RequestNetworkNodesPayload(
                                menu.blockPos()));
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
