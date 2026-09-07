/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.hardware.ComputerBuild;
import dev.jstech.computronics.item.ServerHardwareHandler;
import dev.jstech.computronics.menu.ServerAssemblyMenu;
import dev.jstech.computronics.operation.payload.RenameServerPayload;
import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.core.format.Unit;
import dev.jstech.core.format.UnitFormatter;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Screen for assembling a Server: a flat-dark "computer OS" modal.
 */
public class ServerAssemblyScreen extends AbstractAssemblyScreen<ServerAssemblyMenu> {

    private final UnitFormatter fmt = UnitFormatter.forCurrentLocale();

    public ServerAssemblyScreen(final ServerAssemblyMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 244;
        this.imageHeight = 294;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        /*
         * Name field in the header, since renaming a computer happens here, in its assembly GUI, never via
         * an anvil. Each keystroke syncs the name to the held Server.
         */
        setupNameBox(52, 8, 104, RenameServerPayload.MAX_LEN,
                Component.literal("Name this server...").withStyle(ChatFormatting.DARK_GRAY),
                menu.serverName(),
                s -> PacketDistributor.sendToServer(new RenameServerPayload(s)));
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // Clicking the name field selects it for typing; clicking elsewhere deselects it.
        if (nameBox != null) {
            if (nameBox.isMouseOver(mouseX, mouseY)) {
                setFocused(nameBox);
                nameBox.setFocused(true);
                return nameBox.mouseClicked(mouseX, mouseY, button);
            }
            nameBox.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JsTechTheme.window(g, x, y, imageWidth, imageHeight);
        JsTechTheme.headerBar(g, x + 6, y + 6, 232);
        // Name field background (the field itself is an EditBox drawn over this).
        g.fill(x + 50, y + 7, x + 158, y + 19, 0xFF11161D);
        g.fill(x + 50, y + 18, x + 158, y + 19, 0xFF24323C);

        final ComputerBuild build = menu.currentBuild();

        // Spec tiles (4 across).
        JsTechTheme.panel(g, x + 8, y + 26, 55, 18);
        JsTechTheme.panel(g, x + 67, y + 26, 55, 18);
        JsTechTheme.panel(g, x + 126, y + 26, 55, 18);
        JsTechTheme.panel(g, x + 185, y + 26, 51, 18);

        /*
         * Power-headroom track. There is no storage track any more: drives live in the rack's
         * hotswap bays, so the assembly has no storage of its own to meter.
         */
        final int draw = build == null ? 0 : build.powerDraw();
        final int watt = build == null ? 0 : build.psu().wattage();
        final double pf = watt <= 0 ? 0.0 : Math.min(1.0, (double) draw / watt);
        JsTechTheme.track(g, x + 52, y + 49, 130, pf, draw > watt ? JsTechTheme.red() : JsTechTheme.green());

        // Problems strip.
        JsTechTheme.panel(g, x + 8, y + 68, 228, 12);

        // Draw a cell behind every ACTIVE hardware slot, reading the menu's own slot positions. The
        for (int i = 0; i < ServerHardwareHandler.SLOTS; i++) {
            final var slot = menu.getSlot(i);
            if (slot.isActive()) {
                JsTechTheme.slot(g, x + slot.x, y + slot.y);
            }
        }

        // Player inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JsTechTheme.slot(g, x + 8 + col * 18, y + 214 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JsTechTheme.slot(g, x + 8 + col * 18, y + 272);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        final ComputerBuild build = menu.currentBuild();

        JsTechTheme.text(g, font, "SERVER", 12, 11, JsTechTheme.text());
        final String status;
        final int statusColor;
        if (build == null) {
            status = "UNASSEMBLED";
            statusColor = JsTechTheme.dim();
        } else if (!build.validate().valid()) {
            status = "ERROR";
            statusColor = JsTechTheme.red();
        } else if (build.rams().isEmpty()) {
            status = "WARN";
            statusColor = JsTechTheme.amber();
        } else {
            status = "READY";
            statusColor = JsTechTheme.green();
        }
        final int pillX = 232 - font.width(status);
        JsTechTheme.text(g, font, status, pillX, 11, statusColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, statusColor);

        // Spec tiles.
        JsTechTheme.tileTextS(g, font, 8, 26, "ORCH", build == null ? "0" : fmt.compact(build.totalCapacity(), Unit.IT_PER_TICK), JsTechTheme.text());
        JsTechTheme.tileTextS(g, font, 67, 26, "QUEUES", build == null ? "0" : String.valueOf(build.parallelQueues()), JsTechTheme.text());
        JsTechTheme.tileTextS(g, font, 126, 26, "RAM BUF", build == null ? "0" : JsTechTheme.fmt(build.ramBuffer()), JsTechTheme.text());
        JsTechTheme.tileTextS(g, font, 185, 26, "DRAW", build == null ? "0" : build.powerDraw() + "W", JsTechTheme.text());

        // Track labels + values.
        final int draw = build == null ? 0 : build.powerDraw();
        final int watt = build == null ? 0 : build.psu().wattage();
        JsTechTheme.textS(g, font, "POWER", 8, 49, JsTechTheme.text());
        JsTechTheme.textSRight(g, font, build == null ? "-- W" : draw + "/" + watt + "W", 236, 49,
                draw > watt ? JsTechTheme.red() : JsTechTheme.dim());
        /*
         * Drives live in the rack's hotswap bays since the racks rework, so the assembly has no
         * storage of its own to report, so point the player at the right place instead.
         */
        JsTechTheme.textS(g, font, "STORAGE", 8, 59, JsTechTheme.text());
        JsTechTheme.textSRight(g, font, "drives mount in the rack bays", 236, 59, JsTechTheme.dim());

        // Problems strip.
        renderProblems(g, build);

        // Hardware bay labels (names only, the slots show installed vs available).
        JsTechTheme.textS(g, font, "BOARD", 8, 87, JsTechTheme.dim());
        JsTechTheme.textS(g, font, "CPU", 52, 87, JsTechTheme.dim());
        JsTechTheme.textS(g, font, "RAM", 52, 117, JsTechTheme.dim());
        JsTechTheme.textS(g, font, "GPU", 52, 165, JsTechTheme.dim());
    }

    private void renderProblems(final GuiGraphics g, final ComputerBuild build) {
        if (build == null) {
            JsTechTheme.textS(g, font, "Insert a motherboard and PSU to begin", 12, 71, JsTechTheme.dim());
            return;
        }
        final List<String> problems = build.validate().problems();
        if (problems.isEmpty()) {
            JsTechTheme.textS(g, font, "All checks passed", 12, 71, JsTechTheme.green());
            return;
        }
        final String first = font.plainSubstrByWidth(problems.get(0), 250);
        JsTechTheme.textS(g, font, first, 12, 71, JsTechTheme.red());
        if (problems.size() > 1) {
            JsTechTheme.textSRight(g, font, "+" + (problems.size() - 1) + " more", 234, 71, JsTechTheme.amber());
        }
    }

    @Override
    protected HardwareEra screenEra() {
        return menu.hardwareEra();
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        // super.render binds the era skin, draws the background and widgets, and renders the slot tooltip.
        super.render(g, mouseX, mouseY, partialTick);
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
