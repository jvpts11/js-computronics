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
import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import dev.jsc.jscomputronics.module.computing.menu.ServerRackMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Screen for the Server Rack: a flat-dark "bay manifest".
 */
public class ServerRackScreen extends AbstractContainerScreen<ServerRackMenu> {

    private static final int CAP = ServerRackBlockEntity.CAPACITY;
    private static final int BAY_Y = 70;
    private static final int BAY_PITCH = 18;
    private static final int ROW_LEFT = 8;
    private static final int ROW_W = 228;
    private static final int TRACK_X = 92;
    private static final int TRACK_W = 76;

    private final UnitFormatter fmt = UnitFormatter.forCurrentLocale();

    public ServerRackScreen(final ServerRackMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 244;
        this.imageHeight = 228;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, 232);

        // Summary tiles.
        JscOsTheme.panel(g, x + 8, y + 34, 73, 18);
        JscOsTheme.panel(g, x + 85, y + 34, 73, 18);
        JscOsTheme.panel(g, x + 162, y + 34, 74, 18);
        JscOsTheme.hLine(g, x + 8, y + 56, 228);

        // Bay rows.
        for (int i = 0; i < CAP; i++) {
            final int top = y + BAY_Y + i * BAY_PITCH;
            JscOsTheme.panel(g, x + ROW_LEFT, top, ROW_W, 17);
            final ItemStack server = menu.serverInBay(i);
            final boolean populated = server.getItem() instanceof ServerItem;
            if (populated) {
                g.fill(x + ROW_LEFT + 1, top, x + ROW_LEFT + 3, top + 17, JscOsTheme.accent());
            }
            JscOsTheme.slot(g, x + 12, top + 1);
            if (populated) {
                final ComputerBuild build = ServerItem.build(server);
                if (build != null) {
                    final long cap = build.totalStorageItems();
                    final long used = ServerItem.storage(server).total();
                    final double frac = cap <= 0 ? 0.0 : Math.min(1.0, (double) used / cap);
                    JscOsTheme.track(g, x + TRACK_X, top + 7, TRACK_W, frac, frac >= 0.9 ? JscOsTheme.red() : JscOsTheme.green());
                }
            }
        }

        // Player inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + 8 + col * 18, y + 148 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + 8 + col * 18, y + 206);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        final boolean linked = menu.networkLinked();
        JscOsTheme.text(g, font, "SERVER RACK", 12, 11, JscOsTheme.text());
        final String pill = linked ? "LINKED" : "OFFLINE";
        final int pillColor = linked ? JscOsTheme.green() : JscOsTheme.red();
        final int pillX = 232 - font.width(pill);
        JscOsTheme.text(g, font, pill, pillX, 11, pillColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, pillColor);

        // Aggregate the cabinet.
        int used = 0;
        long totalMb = 0L;
        long totalStored = 0L;
        long totalCapItems = 0L;
        int online = 0;
        for (int i = 0; i < CAP; i++) {
            final ItemStack s = menu.serverInBay(i);
            if (!(s.getItem() instanceof ServerItem)) {
                continue;
            }
            used++;
            final ComputerBuild build = ServerItem.build(s);
            totalMb += ServerItem.storageMb(s);
            totalStored += ServerItem.storage(s).total();
            if (build != null) {
                totalCapItems += build.totalStorageItems();
                if (linked) {
                    online++;
                }
            }
        }

        JscOsTheme.textS(g, font, "RACK SUMMARY", 8, 26, JscOsTheme.dim());
        JscOsTheme.textSRight(g, font, used + " / " + CAP + " bays", 236, 26, JscOsTheme.dim());
        JscOsTheme.tileTextS(g, font, 8, 34, "BAYS", used + "/" + CAP, JscOsTheme.text());
        JscOsTheme.tileTextS(g, font, 85, 34, "CAPACITY", fmt.compact(totalMb, Unit.MB), JscOsTheme.text());
        final int storedColor = totalCapItems > 0 && totalStored >= totalCapItems * 9 / 10 ? JscOsTheme.red() : JscOsTheme.green();
        JscOsTheme.tileTextS(g, font, 162, 34, "STORED", JscOsTheme.fmt(totalStored), storedColor);

        JscOsTheme.textS(g, font, "BAYS", 8, 60, JscOsTheme.dim());
        JscOsTheme.textSRight(g, font, online + " online", 236, 60, JscOsTheme.dim());

        for (int i = 0; i < CAP; i++) {
            final int top = BAY_Y + i * BAY_PITCH;
            final ItemStack server = menu.serverInBay(i);
            if (!(server.getItem() instanceof ServerItem)) {
                JscOsTheme.textS(g, font, "— empty —", 34, top + 6, JscOsTheme.dim());
                continue;
            }
            final ComputerBuild build = ServerItem.build(server);
            final UUID uuid = ServerItem.nodeUuid(server);
            final String id = uuid != null ? uuid.toString().substring(0, 6) : "server";
            JscOsTheme.textS(g, font, id, 34, top + 6, JscOsTheme.text());
            final String state;
            final int color;
            if (build == null) {
                state = "INCOMPLETE";
                color = JscOsTheme.red();
            } else if (linked) {
                state = "ONLINE";
                color = JscOsTheme.green();
            } else {
                state = "READY";
                color = JscOsTheme.amber();
            }
            JscOsTheme.textSRight(g, font, state, 236, top + 6, color);
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderBayTooltip(g, mouseX, mouseY);
    }

    private void renderBayTooltip(final GuiGraphics g, final int mouseX, final int mouseY) {
        final int relX = mouseX - leftPos;
        final int relY = mouseY - topPos;
        if (relX < ROW_LEFT || relX >= ROW_LEFT + ROW_W) {
            return;
        }
        final int row = (relY - BAY_Y) / BAY_PITCH;
        if (row < 0 || row >= CAP || (relY - BAY_Y) % BAY_PITCH > 16) {
            return;
        }
        // The Server slot itself already shows the vanilla item tooltip.
        if (relX >= 11 && relX < 29) {
            return;
        }
        final ItemStack server = menu.serverInBay(row);
        if (!(server.getItem() instanceof ServerItem)) {
            return;
        }
        final List<Component> lines = new ArrayList<>();
        final UUID uuid = ServerItem.nodeUuid(server);
        lines.add(Component.literal(uuid != null ? "Node " + uuid.toString().substring(0, 8) : "Unassigned node"));
        final ComputerBuild build = ServerItem.build(server);
        if (build == null) {
            lines.add(Component.literal("Incomplete — needs a board + PSU").withStyle(ChatFormatting.RED));
        } else {
            final long cap = build.totalStorageItems();
            final long stored = ServerItem.storage(server).total();
            lines.add(Component.literal(stored + " / " + cap + " items").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(fmt.compact(build.storageMb(), Unit.MB) + " capacity").withStyle(ChatFormatting.GRAY));
            if (!menu.networkLinked()) {
                lines.add(Component.literal("Rack cable not on a network").withStyle(ChatFormatting.YELLOW));
            }
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }
}
