/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.SupercomputerNodeMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenamePcPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen for a Supercomputer Node's assembly — the shared "computer OS" skin.
 */
public class SupercomputerNodeScreen extends AbstractAssemblyScreen<SupercomputerNodeMenu> {

    private static final int COL_R = 126;
    private static final int COL_R_W = 110;
    private static final int BTN_H = 14;
    private static final int POWER_Y = 105;
    private static final int AUTO_Y = 121;

    public SupercomputerNodeScreen(final SupercomputerNodeMenu menu, final Inventory inventory,
                                   final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 244;
        this.imageHeight = 218;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        // Name field in the header — a node is renamed here, never via an anvil.
        setupNameBox(28, 8, 126, RenamePcPayload.MAX_LEN,
                Component.literal("Name this node...").withStyle(ChatFormatting.DARK_GRAY),
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

        JscOsTheme.slot(g, x + 8, y + 40);
        if (menu.boardCpuSlots() > 0) {
            JscOsTheme.slot(g, x + 44, y + 40);
        }
        final int ram = Math.min(menu.boardRamSlots(), 2);
        for (int i = 0; i < ram; i++) {
            JscOsTheme.slot(g, x + 80 + i * 18, y + 40);
        }
        if (menu.boardPcieSlots() > 0) {
            JscOsTheme.slot(g, x + 8, y + 73);
        }
        JscOsTheme.slot(g, x + 44, y + 73);
        final int disk = Math.min(menu.boardDiskSlots(), 2);
        for (int i = 0; i < disk; i++) {
            JscOsTheme.slot(g, x + 80 + i * 18, y + 73);
        }

        JscOsTheme.panel(g, x + COL_R, y + 27, COL_R_W, 20);  // CO-PROCESSOR
        JscOsTheme.panel(g, x + COL_R, y + 49, COL_R_W, 20);  // CLUSTER hint

        final boolean auto = menu.isAutoStart();
        JscOsTheme.button(g, x + COL_R, y + POWER_Y, COL_R_W, BTN_H,
                !auto && hover(mouseX, mouseY, COL_R, POWER_Y, COL_R_W, BTN_H));
        JscOsTheme.button(g, x + COL_R, y + AUTO_Y, COL_R_W, BTN_H,
                hover(mouseX, mouseY, COL_R, AUTO_Y, COL_R_W, BTN_H));

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
        JscOsTheme.text(g, font, "NODE", 12, 11, JscOsTheme.text());
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
        JscOsTheme.text(g, font, "RAM", 80, 27, JscOsTheme.dim());
        JscOsTheme.text(g, font, "CO-PROC", 8, 60, menu.hasPhi() ? JscOsTheme.accent() : JscOsTheme.dim());
        JscOsTheme.text(g, font, "PSU", 44, 60, JscOsTheme.dim());
        JscOsTheme.text(g, font, "DISK", 80, 60, JscOsTheme.dim());

        JscOsTheme.tileText(g, font, COL_R, 27, "CO-PROCESSOR",
                menu.hasPhi() ? "SEATED" : "EMPTY", "", menu.hasPhi() ? JscOsTheme.green() : JscOsTheme.dim());
        JscOsTheme.tileText(g, font, COL_R, 49, "CLUSTER",
                "SEE CONSOLE", "", JscOsTheme.dim());

        final boolean auto = menu.isAutoStart();
        final String powerCap = auto ? "AUTO" : (menu.isRunning() ? "TURN OFF" : "TURN ON");
        JscOsTheme.textCenter(g, font, powerCap, COL_R + COL_R_W / 2, POWER_Y + 4,
                auto ? JscOsTheme.dim() : JscOsTheme.accent());
        JscOsTheme.textCenter(g, font, "AUTO: " + (auto ? "ON" : "OFF"), COL_R + COL_R_W / 2, AUTO_Y + 4,
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
            if (!menu.isAutoStart() && hover((int) mouseX, (int) mouseY, COL_R, POWER_Y, COL_R_W, BTN_H)) {
                sendButton(SupercomputerNodeMenu.BUTTON_POWER);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, COL_R, AUTO_Y, COL_R_W, BTN_H)) {
                sendButton(SupercomputerNodeMenu.BUTTON_AUTOSTART);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

}
