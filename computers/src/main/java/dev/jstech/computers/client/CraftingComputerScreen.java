/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import dev.jstech.computers.gui.layout.CraftingComputerLayout;
import dev.jstech.computers.menu.CraftingComputerMenu;
import dev.jstech.computers.operation.payload.RenamePcPayload;
import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Screen for the Crafting Computer's assembly surface, the same flat-dark "computer OS" skin as the
 * Personal Computer. This is a hardware-only surface: recipe files are managed by the Crafting Manager
 * program on the linked monitor, not here.
 */
public class CraftingComputerScreen extends AbstractAssemblyScreen<CraftingComputerMenu> {

    /*
     * All position/size constants live in CraftingComputerLayout so the menu, the screen
     * and the layout unit test share one source of truth.
     */
    private static final int COL_R     = CraftingComputerLayout.COL_R;
    private static final int COL_R_W   = CraftingComputerLayout.COL_R_W;
    private static final int BTN_H     = CraftingComputerLayout.BTN_H;
    private static final int TILE_H    = CraftingComputerLayout.TILE_H;
    private static final int TILE_Y0   = CraftingComputerLayout.TILE_Y0;
    private static final int TILE_Y1   = CraftingComputerLayout.TILE_Y1;
    private static final int TILE_Y2   = CraftingComputerLayout.TILE_Y2;
    private static final int POWER_X   = CraftingComputerLayout.POWER_X;
    private static final int POWER_Y   = CraftingComputerLayout.POWER_Y;
    private static final int AUTO_X    = CraftingComputerLayout.AUTO_X;
    private static final int AUTO_Y    = CraftingComputerLayout.AUTO_Y;
    private static final int INV_X     = CraftingComputerLayout.INV_X;
    private static final int INV_Y     = CraftingComputerLayout.INV_Y;
    private static final int NETWORK_Y = 95;

    public CraftingComputerScreen(final CraftingComputerMenu menu, final Inventory inventory,
                                  final Component title) {
        super(menu, inventory, title);
        this.imageWidth = CraftingComputerLayout.WIDTH;
        this.imageHeight = CraftingComputerLayout.HEIGHT;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        // Name field in the header, since computers are renamed here, never via an anvil.
        setupNameBox(28, 8, 126, RenamePcPayload.MAX_LEN,
                Component.literal("Name this computer...").withStyle(ChatFormatting.DARK_GRAY),
                menu.customName(),
                s -> PacketDistributor.sendToServer(new RenamePcPayload(menu.computerPos(), s)));
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
        final int ram = menu.boardRamSlots();
        final int pcie = menu.boardPcieSlots();
        final int disk = menu.boardDiskSlots();
        for (int i = 0; i < ram; i++) {
            JsTechTheme.slot(g, x + 44 + i * 18, y + 73);
        }
        for (int i = 0; i < pcie; i++) {
            JsTechTheme.slot(g, x + 44 + i * 18, y + 106);
        }
        for (int i = 0; i < disk; i++) {
            JsTechTheme.slot(g, x + 8 + i * 18, y + 106);
        }

        JsTechTheme.panel(g, x + COL_R, y + TILE_Y0, COL_R_W, TILE_H);  // CAPACITY
        JsTechTheme.panel(g, x + COL_R, y + TILE_Y1, COL_R_W, TILE_H);  // CRAFTING
        JsTechTheme.panel(g, x + COL_R, y + TILE_Y2, COL_R_W, TILE_H);  // RECIPE ROM

        final boolean auto = menu.isAutoStart();
        JsTechTheme.button(g, x + POWER_X, y + POWER_Y, COL_R_W, BTN_H,
                !auto && hover(mouseX, mouseY, POWER_X, POWER_Y, COL_R_W, BTN_H));
        JsTechTheme.button(g, x + AUTO_X, y + AUTO_Y, COL_R_W, BTN_H,
                hover(mouseX, mouseY, AUTO_X, AUTO_Y, COL_R_W, BTN_H));

        // Player inventory slots.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JsTechTheme.slot(g, x + INV_X + col * 18, y + INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JsTechTheme.slot(g, x + INV_X + col * 18, y + INV_Y + 58);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JsTechTheme.text(g, font, "CC", 12, 11, JsTechTheme.text());
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
        JsTechTheme.text(g, font, "PCIE", 44, 93,
                menu.craftFactorX100() > 0 ? JsTechTheme.amber() : JsTechTheme.dim());

        JsTechTheme.tileText(g, font, COL_R, TILE_Y0, "CAPACITY", JsTechTheme.fmt(menu.capacity()), "it/t",
                JsTechTheme.text());
        final int factor = menu.craftFactorX100();
        if (factor > 0) {
            /*
             * The Crafting Card is an accelerator with two stats: throughput (this tile's value, factor x CPU) and
             * threads (how many of a craft's stages this computer runs at once, summed over the installed cards).
             */
            JsTechTheme.tileText(g, font, COL_R, TILE_Y1, "CRAFT " + menu.craftThreads() + "T x" + formatFactor(factor),
                    JsTechTheme.fmt(menu.craftThroughput()), "it/t", JsTechTheme.accent());
        } else {
            // No Crafting Card installed: the computer runs but cannot craft.
            JsTechTheme.tileText(g, font, COL_R, TILE_Y1, "CRAFT", "NO CARD", "", JsTechTheme.dim());
        }
        JsTechTheme.tileText(g, font, COL_R, TILE_Y2, "RECIPE ROM",
                menu.romUsed() + " / " + menu.romLimit(), "", JsTechTheme.text());

        JsTechTheme.text(g, font, "NETWORK", COL_R, NETWORK_Y, JsTechTheme.dim());
        if (menu.isOnNetwork()) {
            JsTechTheme.textRight(g, font, "LINKED", COL_R + COL_R_W, NETWORK_Y, JsTechTheme.green());
        } else {
            JsTechTheme.textRight(g, font, "--", COL_R + COL_R_W, NETWORK_Y, JsTechTheme.dim());
        }

        final boolean auto = menu.isAutoStart();
        final String powerCap = auto ? "AUTO" : (menu.isRunning() ? "TURN OFF" : "TURN ON");
        JsTechTheme.textCenter(g, font, powerCap, POWER_X + COL_R_W / 2, POWER_Y + 4,
                auto ? JsTechTheme.dim() : JsTechTheme.accent());
        JsTechTheme.textCenter(g, font, "AUTO: " + (auto ? "ON" : "OFF"), AUTO_X + COL_R_W / 2, AUTO_Y + 4,
                auto ? JsTechTheme.accent() : JsTechTheme.dim());
    }

    private static String formatFactor(final int factorX100) {
        if (factorX100 % 100 == 0) {
            return Integer.toString(factorX100 / 100);
        }
        return String.format(Locale.ROOT, "%.1f", factorX100 / 100.0);
    }

    private int psuColor() {
        if (!menu.hasPsu()) {
            return JsTechTheme.dim();
        }
        return menu.buildValid() ? JsTechTheme.green() : JsTechTheme.amber();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // Computer rename field interaction.
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
