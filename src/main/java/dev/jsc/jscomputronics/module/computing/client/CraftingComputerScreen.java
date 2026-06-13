/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.menu.CraftingComputerMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenamePcPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Screen for the Crafting Computer's assembly surface — the same flat-dark "computer OS" skin as the Personal Computer.
 */
public class CraftingComputerScreen extends AbstractAssemblyScreen<CraftingComputerMenu> {

    private static final int COL_R = 126;
    private static final int COL_R_W = 110;
    private static final int BTN_H = 14;

    // The right column: three 20px-tall tiles at a 22px pitch (tileText draws its
    private static final int TILE_H = 20;
    private static final int TILE_Y0 = 27;
    private static final int TILE_Y1 = 49;
    private static final int TILE_Y2 = 71;
    private static final int NETWORK_Y = 95;
    private static final int POWER_X = COL_R;
    private static final int POWER_Y = 105;
    private static final int AUTO_X = COL_R;
    private static final int AUTO_Y = 121;

    public CraftingComputerScreen(final CraftingComputerMenu menu, final Inventory inventory,
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
        // Name field in the header — computers are renamed here, never via an anvil.
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
        // Name field background (the EditBox is drawn over this).
        g.fill(x + 26, y + 7, x + 158, y + 19, 0xFF11161D);
        g.fill(x + 26, y + 18, x + 158, y + 19, 0xFF24323C);
        JscOsTheme.vLine(g, x + COL_R - 5, y + 24, 108);

        JscOsTheme.slot(g, x + 8, y + 40);  // motherboard
        JscOsTheme.slot(g, x + 8, y + 73);  // psu
        if (menu.boardCpuSlots() > 0) {
            JscOsTheme.slot(g, x + 44, y + 40);
        }
        final int ram = menu.boardRamSlots();
        final int pcie = menu.boardPcieSlots();
        final int disk = menu.boardDiskSlots();
        for (int i = 0; i < ram; i++) {
            JscOsTheme.slot(g, x + 44 + i * 18, y + 73);
        }
        for (int i = 0; i < pcie; i++) {
            JscOsTheme.slot(g, x + 44 + i * 18, y + 106);
        }
        for (int i = 0; i < disk; i++) {
            JscOsTheme.slot(g, x + 8 + i * 18, y + 106);
        }

        JscOsTheme.panel(g, x + COL_R, y + TILE_Y0, COL_R_W, TILE_H);  // CAPACITY
        JscOsTheme.panel(g, x + COL_R, y + TILE_Y1, COL_R_W, TILE_H);  // CRAFTING
        JscOsTheme.panel(g, x + COL_R, y + TILE_Y2, COL_R_W, TILE_H);  // RECIPE ROM

        final boolean auto = menu.isAutoStart();
        JscOsTheme.button(g, x + POWER_X, y + POWER_Y, COL_R_W, BTN_H,
                !auto && hover(mouseX, mouseY, POWER_X, POWER_Y, COL_R_W, BTN_H));
        JscOsTheme.button(g, x + AUTO_X, y + AUTO_Y, COL_R_W, BTN_H,
                hover(mouseX, mouseY, AUTO_X, AUTO_Y, COL_R_W, BTN_H));

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
        JscOsTheme.text(g, font, "CC", 12, 11, JscOsTheme.text());
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
        g.fill(30, 61, 34, 65, psuColor());
        JscOsTheme.text(g, font, "RAM", 44, 60, JscOsTheme.dim());
        JscOsTheme.text(g, font, "DISK", 8, 93, JscOsTheme.dim());
        JscOsTheme.text(g, font, "PCIE", 44, 93,
                menu.craftFactorX100() > 0 ? JscOsTheme.amber() : JscOsTheme.dim());

        JscOsTheme.tileText(g, font, COL_R, TILE_Y0, "CAPACITY", JscOsTheme.fmt(menu.capacity()), "it/t",
                JscOsTheme.text());
        final int factor = menu.craftFactorX100();
        if (factor > 0) {
            JscOsTheme.tileText(g, font, COL_R, TILE_Y1, "CRAFT x" + formatFactor(factor),
                    JscOsTheme.fmt(menu.craftThroughput()), "it/t", JscOsTheme.accent());
        } else {
            // No Crafting Card installed: the computer runs but cannot craft.
            JscOsTheme.tileText(g, font, COL_R, TILE_Y1, "CRAFT", "NO CARD", "", JscOsTheme.dim());
        }
        JscOsTheme.tileText(g, font, COL_R, TILE_Y2, "RECIPE ROM",
                menu.romUsed() + " / " + menu.romLimit(), "", JscOsTheme.text());

        JscOsTheme.text(g, font, "NETWORK", COL_R, NETWORK_Y, JscOsTheme.dim());
        if (menu.isOnNetwork()) {
            JscOsTheme.textRight(g, font, "LINKED", COL_R + COL_R_W, NETWORK_Y, JscOsTheme.green());
        } else {
            JscOsTheme.textRight(g, font, "--", COL_R + COL_R_W, NETWORK_Y, JscOsTheme.dim());
        }

        final boolean auto = menu.isAutoStart();
        final String powerCap = auto ? "AUTO" : (menu.isRunning() ? "TURN OFF" : "TURN ON");
        JscOsTheme.textCenter(g, font, powerCap, POWER_X + COL_R_W / 2, POWER_Y + 4,
                auto ? JscOsTheme.dim() : JscOsTheme.accent());
        JscOsTheme.textCenter(g, font, "AUTO: " + (auto ? "ON" : "OFF"), AUTO_X + COL_R_W / 2, AUTO_Y + 4,
                auto ? JscOsTheme.accent() : JscOsTheme.dim());
    }

    private static String formatFactor(final int factorX100) {
        if (factorX100 % 100 == 0) {
            return Integer.toString(factorX100 / 100);
        }
        return String.format(Locale.ROOT, "%.1f", factorX100 / 100.0);
    }

    private int psuColor() {
        if (!menu.hasPsu()) {
            return JscOsTheme.dim();
        }
        return menu.buildValid() ? JscOsTheme.green() : JscOsTheme.amber();
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
                sendButton(CraftingComputerMenu.BUTTON_POWER);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, AUTO_X, AUTO_Y, COL_R_W, BTN_H)) {
                sendButton(CraftingComputerMenu.BUTTON_AUTOSTART);
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
