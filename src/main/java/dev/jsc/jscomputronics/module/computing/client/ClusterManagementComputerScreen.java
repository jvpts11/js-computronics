/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.gui.layout.ClusterManagementComputerLayout;
import dev.jsc.jscomputronics.module.computing.menu.ClusterManagementComputerMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenamePcPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Cluster Management Computer's assembly surface: hardware on the left, and on the right what makes
 * it a cluster master — whether the interface card is in, how many clusters the network reaches, and
 * whether the Cluster Manager is on its disk. Everything about the clusters themselves lives in that
 * program on the linked monitor, not here.
 */
public class ClusterManagementComputerScreen extends AbstractAssemblyScreen<ClusterManagementComputerMenu> {

    private static final int COL_R = ClusterManagementComputerLayout.COL_R;
    private static final int COL_R_W = ClusterManagementComputerLayout.COL_R_W;
    private static final int BTN_H = ClusterManagementComputerLayout.BTN_H;
    private static final int TILE_H = ClusterManagementComputerLayout.TILE_H;
    private static final int TILE_Y0 = ClusterManagementComputerLayout.TILE_Y0;
    private static final int TILE_Y1 = ClusterManagementComputerLayout.TILE_Y1;
    private static final int TILE_Y2 = ClusterManagementComputerLayout.TILE_Y2;
    private static final int TILE_Y3 = ClusterManagementComputerLayout.TILE_Y3;
    private static final int POWER_X = ClusterManagementComputerLayout.POWER_X;
    private static final int POWER_Y = ClusterManagementComputerLayout.POWER_Y;
    private static final int AUTO_X = ClusterManagementComputerLayout.AUTO_X;
    private static final int AUTO_Y = ClusterManagementComputerLayout.AUTO_Y;
    private static final int INV_X = ClusterManagementComputerLayout.INV_X;
    private static final int INV_Y = ClusterManagementComputerLayout.INV_Y;

    public ClusterManagementComputerScreen(final ClusterManagementComputerMenu menu, final Inventory inventory,
                                           final Component title) {
        super(menu, inventory, title);
        this.imageWidth = ClusterManagementComputerLayout.WIDTH;
        this.imageHeight = ClusterManagementComputerLayout.HEIGHT;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        setupNameBox(28, 8, 126, RenamePcPayload.MAX_LEN,
                Component.literal("Name this computer...").withStyle(ChatFormatting.DARK_GRAY),
                menu.customName(),
                s -> PacketDistributor.sendToServer(new RenamePcPayload(menu.computerPos(), s)));
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, 232);
        g.fill(x + 26, y + 7, x + 158, y + 19, 0xFF11161D);
        g.fill(x + 26, y + 18, x + 158, y + 19, 0xFF24323C);
        JscOsTheme.vLine(g, x + COL_R - 5, y + 24, 108);
        JscOsTheme.slot(g, x + ClusterManagementComputerLayout.MOBO_X, y + ClusterManagementComputerLayout.MOBO_Y);
        JscOsTheme.slot(g, x + ClusterManagementComputerLayout.PSU_X, y + ClusterManagementComputerLayout.PSU_Y);
        if (menu.boardCpuSlots() > 0) {
            JscOsTheme.slot(g, x + ClusterManagementComputerLayout.RIGHT_X, y + ClusterManagementComputerLayout.CPU_Y);
        }
        for (int i = 0; i < menu.boardRamSlots(); i++) {
            JscOsTheme.slot(g, x + ClusterManagementComputerLayout.RIGHT_X + i * 18, y + ClusterManagementComputerLayout.RAM_Y);
        }
        for (int i = 0; i < menu.boardPcieSlots(); i++) {
            JscOsTheme.slot(g, x + ClusterManagementComputerLayout.RIGHT_X + i * 18, y + ClusterManagementComputerLayout.PCIE_Y);
        }
        for (int i = 0; i < menu.boardDiskSlots(); i++) {
            JscOsTheme.slot(g, x + ClusterManagementComputerLayout.MOBO_X + i * 18, y + ClusterManagementComputerLayout.DISK_Y);
        }
        JscOsTheme.panel(g, x + COL_R, y + TILE_Y0, COL_R_W, TILE_H);
        JscOsTheme.panel(g, x + COL_R, y + TILE_Y1, COL_R_W, TILE_H);
        JscOsTheme.panel(g, x + COL_R, y + TILE_Y2, COL_R_W, TILE_H);
        JscOsTheme.panel(g, x + COL_R, y + TILE_Y3, COL_R_W, TILE_H);
        final boolean auto = menu.isAutoStart();
        JscOsTheme.button(g, x + POWER_X, y + POWER_Y, COL_R_W, BTN_H,
                !auto && hover(mouseX, mouseY, POWER_X, POWER_Y, COL_R_W, BTN_H));
        JscOsTheme.button(g, x + AUTO_X, y + AUTO_Y, COL_R_W, BTN_H,
                hover(mouseX, mouseY, AUTO_X, AUTO_Y, COL_R_W, BTN_H));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + INV_X + col * 18, y + INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + INV_X + col * 18, y + INV_Y + 58);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "CMC", 12, 11, JscOsTheme.text());
        final String status;
        final int statusColor;
        if (!menu.buildValid()) {
            status = "OFFLINE";
            statusColor = JscOsTheme.red();
        } else if (menu.isRunning()) {
            status = "ONLINE";
            statusColor = JscOsTheme.green();
        } else {
            status = "READY";
            statusColor = JscOsTheme.amber();
        }
        final int pillX = 232 - font.width(status);
        JscOsTheme.text(g, font, status, pillX, 11, statusColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, statusColor);

        JscOsTheme.text(g, font, "BOARD", 8, 27, menu.hasBoard() ? JscOsTheme.accent() : JscOsTheme.dim());
        JscOsTheme.text(g, font, "CPU", 44, 27, JscOsTheme.dim());
        JscOsTheme.text(g, font, "PSU", 8, 60, JscOsTheme.dim());
        g.fill(30, 61, 34, 65, !menu.hasPsu() ? JscOsTheme.dim() : menu.buildValid() ? JscOsTheme.green() : JscOsTheme.amber());
        JscOsTheme.text(g, font, "RAM", 44, 60, JscOsTheme.dim());
        JscOsTheme.text(g, font, "DISK", 8, 93, JscOsTheme.dim());
        JscOsTheme.text(g, font, "PCIE", 44, 93, menu.hasCard() ? JscOsTheme.accent() : JscOsTheme.dim());

        // Small-font tiles: four fit where the crafting computer keeps three.
        JscOsTheme.tileTextS(g, font, COL_R, TILE_Y0, "CAPACITY", JscOsTheme.fmt(menu.capacity()) + " it/t · "
                + JscOsTheme.fmt(menu.ramBuffer()) + " it", JscOsTheme.text());
        JscOsTheme.tileTextS(g, font, COL_R, TILE_Y1, "NETWORK", menu.isOnNetwork() ? "LINKED" : "no link",
                menu.isOnNetwork() ? JscOsTheme.green() : JscOsTheme.dim());
        if (!menu.hasCard()) {
            JscOsTheme.tileTextS(g, font, COL_R, TILE_Y2, "CLUSTERS IN REACH", "no interface card", JscOsTheme.amber());
        } else {
            JscOsTheme.tileTextS(g, font, COL_R, TILE_Y2, "CLUSTERS IN REACH", menu.supercomputers() + " SC · "
                    + menu.datacenters() + " DC · " + menu.lanes() + " lanes", JscOsTheme.text());
        }
        JscOsTheme.tileTextS(g, font, COL_R, TILE_Y3, "MANAGEMENT",
                menu.managerInstalled() ? "Cluster Manager · installed" : "Cluster Manager · not installed",
                menu.managerInstalled() ? JscOsTheme.accent() : JscOsTheme.dim());

        final boolean auto = menu.isAutoStart();
        final String powerCap = auto ? "AUTO" : (menu.isRunning() ? "TURN OFF" : "TURN ON");
        JscOsTheme.textCenter(g, font, powerCap, POWER_X + COL_R_W / 2, POWER_Y + 4,
                auto ? JscOsTheme.dim() : JscOsTheme.accent());
        JscOsTheme.textCenter(g, font, "AUTO: " + (auto ? "ON" : "OFF"), AUTO_X + COL_R_W / 2, AUTO_Y + 4,
                auto ? JscOsTheme.accent() : JscOsTheme.dim());
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (nameBox != null) {
            if (nameBox.isMouseOver(mouseX, mouseY)) {
                setFocused(nameBox);
                nameBox.setFocused(true);
                return nameBox.mouseClicked(mouseX, mouseY, button);
            }
            nameBox.setFocused(false);
        }
        if (button == 0) {
            if (!menu.isAutoStart() && hover((int) mouseX, (int) mouseY, POWER_X, POWER_Y, COL_R_W, BTN_H)) {
                sendButton(ClusterManagementComputerMenu.BUTTON_POWER);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, AUTO_X, AUTO_Y, COL_R_W, BTN_H)) {
                sendButton(ClusterManagementComputerMenu.BUTTON_AUTOSTART);
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
