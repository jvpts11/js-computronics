/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.datacenter.LoadBalanceMode;
import dev.jsc.jscomputronics.module.computing.menu.ServerRouterMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenameServerRouterPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Config GUI for the Server Router: a flat-dark panel matching the computer-OS theme.
 */
public final class ServerRouterScreen extends AbstractContainerScreen<ServerRouterMenu> {

    private static final int W = 190;
    private static final int H = 176;
    private static final int ROW_Y0 = 96;
    private static final int ROW_PITCH = 15;
    private static final int MODE_X = 108;
    private static final int MODE_W = 74;
    private static final int MODE_H = 12;

    private EditBox nameBox;

    public ServerRouterScreen(final ServerRouterMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        // No vanilla labels — the panel draws its own.
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;

        nameBox = new EditBox(font, leftPos + 10, topPos + 39, 170, 10, Component.literal("Name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(RenameServerRouterPayload.MAX_LEN);
        nameBox.setTextColor(JscOsTheme.TEXT);
        nameBox.setValue(menu.initialName());
        nameBox.setResponder(s ->
                PacketDistributor.sendToServer(new RenameServerRouterPayload(menu.routerPos(), s)));
        addRenderableWidget(nameBox);
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, W, H);
        JscOsTheme.headerBar(g, x + 6, y + 6, W - 12);

        // Name field background (the EditBox is drawn over this).
        g.fill(x + 8, y + 36, x + W - 8, y + 50, JscOsTheme.SLOT_BG);
        g.fill(x + 8, y + 36, x + W - 8, y + 37, JscOsTheme.LINE);

        // Two status tiles.
        JscOsTheme.panel(g, x + 8, y + 56, 84, 22);
        JscOsTheme.panel(g, x + 98, y + 56, 84, 22);

        // Section list separator.
        JscOsTheme.hLine(g, x + 8, y + 93, W - 16);

        // One mode button per section row.
        for (int i = 0; i < menu.sectionCount(); i++) {
            final int by = y + ROW_Y0 + i * ROW_PITCH;
            final boolean hovered = mouseX >= x + MODE_X && mouseX < x + MODE_X + MODE_W
                    && mouseY >= by && mouseY < by + MODE_H;
            JscOsTheme.button(g, x + MODE_X, by, MODE_W, MODE_H, hovered);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        // Header.
        JscOsTheme.text(g, font, "SERVER ROUTER", 12, 10, JscOsTheme.TEXT);
        JscOsTheme.textRight(g, font, "T3", W - 12, 10, JscOsTheme.ACCENT);

        JscOsTheme.textS(g, font, "NAME", 10, 28, JscOsTheme.DIM);

        // Input + rack-budget tiles.
        final Direction in = menu.inputFace();
        JscOsTheme.tileTextS(g, font, 8, 56, "INPUT", in == null ? "—" : title(in.getName()), JscOsTheme.ACCENT2);
        final int max = menu.maxRacks();
        final String racks = menu.managedRacks() + " / " + max;
        JscOsTheme.tileTextS(g, font, 98, 56, "RACKS",
                racks, menu.overCapacity() ? JscOsTheme.RED : JscOsTheme.GREEN);

        JscOsTheme.textS(g, font, "SECTIONS", 10, 84, JscOsTheme.DIM);

        final int count = menu.sectionCount();
        if (count == 0) {
            JscOsTheme.textS(g, font, "No datacenter sections", 12, ROW_Y0 + 3, JscOsTheme.DIM);
            return;
        }
        for (int i = 0; i < count; i++) {
            final int ry = ROW_Y0 + i * ROW_PITCH;
            final Direction face = menu.sectionFace(i);
            JscOsTheme.textS(g, font, face == null ? "?" : face.getName().toUpperCase(java.util.Locale.ROOT),
                    10, ry + 3, JscOsTheme.TEXT);
            JscOsTheme.textS(g, font, menu.sectionRacks(i) + "R · " + menu.sectionServers(i) + "S",
                    40, ry + 3, JscOsTheme.DIM);
            final LoadBalanceMode mode = menu.sectionMode(i);
            JscOsTheme.textSCenter(g, font, modeLabel(mode), MODE_X + MODE_W / 2, ry + 3, modeColor(mode));
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            for (int i = 0; i < menu.sectionCount(); i++) {
                final int by = topPos + ROW_Y0 + i * ROW_PITCH;
                if (mouseX >= leftPos + MODE_X && mouseX < leftPos + MODE_X + MODE_W
                        && mouseY >= by && mouseY < by + MODE_H) {
                    final Direction face = menu.sectionFace(i);
                    if (face != null && minecraft != null && minecraft.gameMode != null) {
                        // Cycle this section's load-balance mode via the menu button channel.
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, face.get3DDataValue());
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    private static String modeLabel(final LoadBalanceMode mode) {
        return switch (mode) {
            case ROUND_ROBIN -> "ROUND-ROBIN";
            case LEAST_LOADED -> "LEAST-LOADED";
            case MANUAL -> "MANUAL";
        };
    }

    private static int modeColor(final LoadBalanceMode mode) {
        return switch (mode) {
            case ROUND_ROBIN -> JscOsTheme.ACCENT2;
            case LEAST_LOADED -> JscOsTheme.AMBER;
            case MANUAL -> JscOsTheme.DIM;
        };
    }

    private static String title(final String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
