/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenamePcPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Screen for the Personal Computer: a flat-dark "computer OS" hardware-assembly surface, the same skin as the Mainframe.
 */
public class PersonalComputerScreen extends AbstractComputerScreen<PersonalComputerMenu> {

    private static final int COL_R = 126;
    private static final int COL_R_W = 110;
    private static final int BTN_H = 14;

    private static final int POWER_X = COL_R;
    private static final int POWER_Y = 98;
    private static final int AUTO_X = COL_R;
    private static final int AUTO_Y = 116;

    private EditBox nameBox;

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
        // Name field in the header — a PC is renamed here, in its assembly GUI, never via an anvil.
        nameBox = new EditBox(font, leftPos + 28, topPos + 8, 126, 11, Component.literal("Name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(RenamePcPayload.MAX_LEN);
        nameBox.setTextColor(JscOsTheme.TEXT);
        nameBox.setHint(Component.literal("Name this PC...").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(menu.customName());
        nameBox.setResponder(s -> PacketDistributor.sendToServer(new RenamePcPayload(menu.pcPos(), s)));
        addRenderableWidget(nameBox);
    }

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        // While the name field has focus, route typing to it and never let a key (e.g. the inventory
        // key 'E') reach the screen and close the GUI. ESC just unfocuses the field.
        if (nameBox != null && nameBox.isFocused()) {
            if (key == 256) {
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            nameBox.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(final char c, final int mods) {
        if (nameBox != null && nameBox.isFocused()) {
            return nameBox.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
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
        // The board-derived counts are already clamped to the chassis bays in the BlockEntity, so the
        // screen draws exactly what the menu exposes — one source of truth, no duplicated cap literal.
        final int ram = menu.boardRamSlots();
        final int gpu = menu.boardPcieSlots();
        final int disk = menu.boardDiskSlots();
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
        JscOsTheme.text(g, font, "PC", 12, 11, JscOsTheme.TEXT);
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
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
