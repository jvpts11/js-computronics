/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.PersonalComputerMenu;
import dev.jstech.computronics.operation.payload.RenamePcPayload;
import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen for the Personal Computer: a flat-dark "computer OS" hardware-assembly surface, the same skin as the Mainframe.
 */
public class PersonalComputerScreen extends AbstractAssemblyScreen<PersonalComputerMenu> {

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
    protected void init() {
        super.init();
        // Name field in the header, since a PC is renamed here, in its assembly GUI, never via an anvil.
        setupNameBox(28, 8, 126, RenamePcPayload.MAX_LEN,
                Component.literal("Name this PC...").withStyle(ChatFormatting.DARK_GRAY),
                menu.customName(),
                s -> PacketDistributor.sendToServer(new RenamePcPayload(menu.pcPos(), s)));
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JsTechTheme.window(g, x, y, imageWidth, imageHeight);
        JsTechTheme.headerBar(g, x + 6, y + 6, 232);
        // Name field background (the EditBox is drawn over this).
        g.fill(x + 26, y + 7, x + 158, y + 19, 0xFF11161D);
        g.fill(x + 26, y + 18, x + 158, y + 19, 0xFF24323C);
        JsTechTheme.vLine(g, x + COL_R - 5, y + 24, 108);

        JsTechTheme.slot(g, x + 8, y + 40);  // motherboard
        JsTechTheme.slot(g, x + 8, y + 73);  // psu
        if (menu.boardCpuSlots() > 0) {
            JsTechTheme.slot(g, x + 44, y + 40);
        }
        /*
         * The board-derived counts are already clamped to the chassis bays in the BlockEntity, so the
         * screen draws exactly what the menu exposes, one source of truth, no duplicated cap literal.
         */
        final int ram = menu.boardRamSlots();
        final int gpu = menu.boardPcieSlots();
        final int disk = menu.boardDiskSlots();
        for (int i = 0; i < ram; i++) {
            JsTechTheme.slot(g, x + 44 + i * 18, y + 73);
        }
        for (int i = 0; i < gpu; i++) {
            JsTechTheme.slot(g, x + 44 + i * 18, y + 106);
        }
        for (int i = 0; i < disk; i++) {
            JsTechTheme.slot(g, x + 8 + i * 18, y + 106);
        }

        JsTechTheme.panel(g, x + COL_R, y + 27, COL_R_W, 22);  // CAPACITY
        JsTechTheme.panel(g, x + COL_R, y + 52, COL_R_W, 18);  // RAM BUFFER

        final boolean auto = menu.isAutoStart();
        JsTechTheme.button(g, x + POWER_X, y + POWER_Y, COL_R_W, BTN_H,
                !auto && hover(mouseX, mouseY, POWER_X, POWER_Y, COL_R_W, BTN_H));
        JsTechTheme.button(g, x + AUTO_X, y + AUTO_Y, COL_R_W, BTN_H, hover(mouseX, mouseY, AUTO_X, AUTO_Y, COL_R_W, BTN_H));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JsTechTheme.slot(g, x + 8 + col * 18, y + 138 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JsTechTheme.slot(g, x + 8 + col * 18, y + 196);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JsTechTheme.text(g, font, "PC", 12, 11, JsTechTheme.text());
        final String status;
        final int statusColor;
        if (!menu.buildValid()) {
            status = "OFFLINE";
            statusColor = JsTechTheme.red();
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

        JsTechTheme.text(g, font, "BOARD", 8, 27, menu.hasBoard() ? JsTechTheme.accent() : JsTechTheme.dim());
        JsTechTheme.text(g, font, "CPU", 44, 27, JsTechTheme.dim());
        JsTechTheme.text(g, font, "PSU", 8, 60, JsTechTheme.dim());
        g.fill(30, 61, 34, 65, psuColor());
        JsTechTheme.text(g, font, "RAM", 44, 60, JsTechTheme.dim());
        JsTechTheme.text(g, font, "DISK", 8, 93, JsTechTheme.dim());
        JsTechTheme.text(g, font, "GPU", 44, 93, JsTechTheme.dim());

        JsTechTheme.tileText(g, font, COL_R, 27, "CAPACITY", JsTechTheme.fmt(menu.capacity()), "it/t", JsTechTheme.text());
        JsTechTheme.tileText(g, font, COL_R, 52, "RAM BUFFER", JsTechTheme.fmt(menu.ramBuffer()), "it", JsTechTheme.text());

        JsTechTheme.text(g, font, "NETWORK", COL_R, 74, JsTechTheme.dim());
        if (menu.isOnNetwork()) {
            JsTechTheme.textRight(g, font, "LINKED", COL_R + COL_R_W, 74, JsTechTheme.green());
            final int n = menu.networkServerCount();
            JsTechTheme.textRight(g, font, n + (n == 1 ? " server" : " servers"), COL_R + COL_R_W, 85, JsTechTheme.dim());
        } else {
            JsTechTheme.textRight(g, font, "--", COL_R + COL_R_W, 74, JsTechTheme.dim());
        }

        final boolean auto = menu.isAutoStart();
        final String powerCap = auto ? "AUTO" : (menu.isRunning() ? "TURN OFF" : "TURN ON");
        JsTechTheme.textCenter(g, font, powerCap, POWER_X + COL_R_W / 2, POWER_Y + 4, auto ? JsTechTheme.dim() : JsTechTheme.accent());
        JsTechTheme.textCenter(g, font, "AUTO: " + (auto ? "ON" : "OFF"), AUTO_X + COL_R_W / 2, AUTO_Y + 4,
                auto ? JsTechTheme.accent() : JsTechTheme.dim());
    }

    private int psuColor() {
        if (!menu.hasPsu()) {
            return JsTechTheme.dim();
        }
        return menu.buildValid() ? JsTechTheme.green() : JsTechTheme.amber();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // Clicking the name field selects it for typing; clicking anywhere else deselects it.
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

    @Override
    protected HardwareEra screenEra() {
        return menu.hardwareEra();
    }
}
