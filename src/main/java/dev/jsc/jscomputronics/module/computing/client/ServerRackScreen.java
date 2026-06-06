/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.common.format.Unit;
import dev.jsc.jscomputronics.common.format.UnitFormatter;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import dev.jsc.jscomputronics.module.computing.menu.ServerRackMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Screen for the Server Rack: the housed Servers as vertical 1U bays, each with rack rails, mounting holes, the Server slot, its label, and a link LED — above the player inventory.
 */
public class ServerRackScreen extends AbstractContainerScreen<ServerRackMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int RAIL = 0xFFA8A8A8;
    private static final int RAIL_LIGHT = 0xFFD4D4D4;
    private static final int RAIL_DARK = 0xFF6F6F6F;
    private static final int HOLE = 0xFF5A5A5A;
    private static final int LABEL = 0xFF404040;
    private static final int EMPTY_LABEL = 0xFF7A7A7A;
    private static final int LED_ON = 0xFF3DA53D;
    private static final int LED_OFF = 0xFF777777;

    private final UnitFormatter fmt = UnitFormatter.forCurrentLocale();

    public ServerRackScreen(final ServerRackMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 254;
        this.inventoryLabelY = 168;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 1, BEVEL_LIGHT);
        g.fill(x, y, x + 1, y + imageHeight, BEVEL_LIGHT);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BEVEL_DARK);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BEVEL_DARK);

        for (int i = 0; i < ServerRackBlockEntity.CAPACITY; i++) {
            final int by = y + 22 + i * 18;
            // 1U rail: a bevelled bar spanning the panel, with two mounting holes.
            g.fill(x + 6, by - 1, x + 170, by + 17, RAIL);
            g.fill(x + 6, by - 1, x + 170, by, RAIL_LIGHT);
            g.fill(x + 6, by - 1, x + 7, by + 17, RAIL_LIGHT);
            g.fill(x + 6, by + 16, x + 170, by + 17, RAIL_DARK);
            g.fill(x + 169, by - 1, x + 170, by + 17, RAIL_DARK);
            g.fill(x + 9, by + 5, x + 15, by + 11, HOLE);
            g.fill(x + 161, by + 5, x + 167, by + 11, HOLE);
            slot(g, x + 14, by);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + 178 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + 236);
        }
    }

    private static void slot(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        super.renderLabels(g, mouseX, mouseY);

        // Header network-link indicator.
        final boolean linked = menu.networkLinked();
        led(g, 122, 6, linked);
        g.drawString(font, linked ? "Linked" : "Offline", 133, 6, linked ? LED_ON : EMPTY_LABEL, false);

        for (int i = 0; i < ServerRackBlockEntity.CAPACITY; i++) {
            final int ty = 22 + i * 18 + 5;
            final ItemStack server = menu.serverInBay(i);
            if (server.getItem() instanceof ServerItem) {
                final boolean assembled = ServerItem.build(server) != null;
                final UUID uuid = ServerItem.nodeUuid(server);
                final String id = uuid != null ? uuid.toString().substring(0, 6) : "server";
                final long mb = ServerItem.storageMb(server);
                final String text = assembled ? id + "  " + fmt.compact(mb, Unit.MB) : id + "  (incomplete)";
                g.drawString(font, text, 36, ty, LABEL, false);
                led(g, 154, ty, linked && assembled);
            } else {
                g.drawString(font, "— empty —", 36, ty, EMPTY_LABEL, false);
            }
        }
    }

    private static void led(final GuiGraphics g, final int x, final int y, final boolean on) {
        final int c = on ? LED_ON : LED_OFF;
        g.fill(x + 1, y, x + 6, y + 7, c);
        g.fill(x, y + 1, x + 7, y + 6, c);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
