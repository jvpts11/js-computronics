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
import dev.jsc.jscomputronics.module.computing.menu.ServerAssemblyMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.UUID;

/**
 * Screen for assembling a Server: a flat-dark "computer OS" modal.
 */
public class ServerAssemblyScreen extends AbstractContainerScreen<ServerAssemblyMenu> {

    private final UnitFormatter fmt = UnitFormatter.forCurrentLocale();

    public ServerAssemblyScreen(final ServerAssemblyMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 244;
        this.imageHeight = 294;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, 232);

        final ComputerBuild build = menu.currentBuild();

        // Spec tiles (4 across).
        JscOsTheme.panel(g, x + 8, y + 26, 55, 18);
        JscOsTheme.panel(g, x + 67, y + 26, 55, 18);
        JscOsTheme.panel(g, x + 126, y + 26, 55, 18);
        JscOsTheme.panel(g, x + 185, y + 26, 51, 18);

        // Power-headroom + storage tracks.
        final int draw = build == null ? 0 : build.powerDraw();
        final int watt = build == null ? 0 : build.psu().wattage();
        final double pf = watt <= 0 ? 0.0 : Math.min(1.0, (double) draw / watt);
        JscOsTheme.track(g, x + 52, y + 49, 130, pf, draw > watt ? JscOsTheme.RED : JscOsTheme.GREEN);
        final long cap = build == null ? 0L : build.totalStorageItems();
        final long used = menu.storedItems();
        final double sf = cap <= 0 ? 0.0 : Math.min(1.0, (double) used / cap);
        JscOsTheme.track(g, x + 52, y + 59, 130, sf, sf >= 0.9 ? JscOsTheme.RED : JscOsTheme.GREEN);

        // Problems strip.
        JscOsTheme.panel(g, x + 8, y + 68, 228, 12);

        // Hardware bay cells.
        JscOsTheme.slot(g, x + 8, y + 96);    // motherboard
        JscOsTheme.slot(g, x + 26, y + 96);   // psu
        for (int i = 0; i < 6; i++) {
            JscOsTheme.slot(g, x + 8 + (i % 2) * 18, y + 126 + (i / 2) * 18);
        }
        for (int i = 0; i < 4; i++) {
            JscOsTheme.slot(g, x + 52 + i * 18, y + 96);
        }
        for (int i = 0; i < 8; i++) {
            JscOsTheme.slot(g, x + 52 + (i % 4) * 18, y + 126 + (i / 4) * 18);
        }
        for (int i = 0; i < 6; i++) {
            JscOsTheme.slot(g, x + 52 + (i % 3) * 18, y + 174 + (i / 3) * 18);
        }

        // Player inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + 8 + col * 18, y + 214 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + 8 + col * 18, y + 272);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        final ComputerBuild build = menu.currentBuild();

        JscOsTheme.text(g, font, "SERVER ASSEMBLY", 12, 11, JscOsTheme.TEXT);
        final String status;
        final int statusColor;
        if (build == null) {
            status = "UNASSEMBLED";
            statusColor = JscOsTheme.DIM;
        } else if (!build.validate().valid()) {
            status = "ERROR";
            statusColor = JscOsTheme.RED;
        } else if (build.rams().isEmpty()) {
            status = "WARN";
            statusColor = JscOsTheme.AMBER;
        } else {
            status = "READY";
            statusColor = JscOsTheme.GREEN;
        }
        final int pillX = 232 - font.width(status);
        JscOsTheme.text(g, font, status, pillX, 11, statusColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, statusColor);
        final UUID uuid = menu.nodeUuid();
        if (uuid != null) {
            JscOsTheme.textSRight(g, font, uuid.toString().substring(0, 8), pillX - 8, 12, JscOsTheme.DIM);
        }

        // Spec tiles.
        JscOsTheme.tileTextS(g, font, 8, 26, "ORCH", build == null ? "0" : fmt.compact(build.totalCapacity(), Unit.IT_PER_TICK), JscOsTheme.TEXT);
        JscOsTheme.tileTextS(g, font, 67, 26, "QUEUES", build == null ? "0" : String.valueOf(build.parallelQueues()), JscOsTheme.TEXT);
        JscOsTheme.tileTextS(g, font, 126, 26, "RAM BUF", build == null ? "0" : JscOsTheme.fmt(build.ramBuffer()), JscOsTheme.TEXT);
        JscOsTheme.tileTextS(g, font, 185, 26, "DRAW", build == null ? "0" : build.powerDraw() + "W", JscOsTheme.TEXT);

        // Track labels + values.
        final int draw = build == null ? 0 : build.powerDraw();
        final int watt = build == null ? 0 : build.psu().wattage();
        JscOsTheme.textS(g, font, "POWER", 8, 49, JscOsTheme.TEXT);
        JscOsTheme.textSRight(g, font, build == null ? "-- W" : draw + "/" + watt + "W", 236, 49,
                draw > watt ? JscOsTheme.RED : JscOsTheme.DIM);
        final long cap = build == null ? 0L : build.totalStorageItems();
        JscOsTheme.textS(g, font, "STORAGE", 8, 59, JscOsTheme.TEXT);
        JscOsTheme.textSRight(g, font, JscOsTheme.fmt(menu.storedItems()) + "/" + JscOsTheme.fmt(cap), 236, 59, JscOsTheme.DIM);

        // Problems strip.
        renderProblems(g, build);

        // Hardware bay labels (names only — the slots show installed vs available).
        JscOsTheme.textS(g, font, "BOARD", 8, 87, JscOsTheme.DIM);
        JscOsTheme.textS(g, font, "CPU", 52, 87, JscOsTheme.DIM);
        JscOsTheme.textS(g, font, "DISK", 8, 117, JscOsTheme.DIM);
        JscOsTheme.textS(g, font, "RAM", 52, 117, JscOsTheme.DIM);
        JscOsTheme.textS(g, font, "GPU", 52, 165, JscOsTheme.DIM);
    }

    private void renderProblems(final GuiGraphics g, final ComputerBuild build) {
        if (build == null) {
            JscOsTheme.textS(g, font, "Insert a motherboard and PSU to begin", 12, 71, JscOsTheme.DIM);
            return;
        }
        final List<String> problems = build.validate().problems();
        if (problems.isEmpty()) {
            JscOsTheme.textS(g, font, "All checks passed", 12, 71, JscOsTheme.GREEN);
            return;
        }
        final String first = font.plainSubstrByWidth(problems.get(0), 250);
        JscOsTheme.textS(g, font, first, 12, 71, JscOsTheme.RED);
        if (problems.size() > 1) {
            JscOsTheme.textSRight(g, font, "+" + (problems.size() - 1) + " more", 234, 71, JscOsTheme.AMBER);
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // Full problem list on hover over the strip.
        final int relX = mouseX - leftPos;
        final int relY = mouseY - topPos;
        if (relX >= 8 && relX < 236 && relY >= 68 && relY < 80) {
            final ComputerBuild build = menu.currentBuild();
            if (build != null) {
                final List<String> problems = build.validate().problems();
                if (problems.size() > 1) {
                    g.renderComponentTooltip(font,
                            problems.stream().map(p -> (Component) Component.literal(p).withStyle(ChatFormatting.RED)).toList(),
                            mouseX, mouseY);
                }
            }
        }
    }
}
