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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Server assembly GUI: hardware bays grouped by category (board and PSU, storage, CPUs, RAM, GPUs), a live build summary on the right, and the player inventory.
 */
public class ServerAssemblyScreen extends AbstractContainerScreen<ServerAssemblyMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int GROUP_FILL = 0xFFB2B2B2;
    private static final int GROUP_BORDER = 0xFF8A8A8A;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int DIVIDER = 0xFF9A9A9A;
    private static final int LABEL = 0xFF404040;
    private static final int GREEN = 0xFF3DA53D;
    private static final int YELLOW = 0xFFB0902A;
    private static final int RED = 0xFFA53D3D;
    private static final int PANEL_X = 128;
    private static final int DIVIDER_X = 124;

    private final UnitFormatter fmt = UnitFormatter.forCurrentLocale();

    public ServerAssemblyScreen(final ServerAssemblyMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 236;
        this.inventoryLabelY = 150;
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
        g.fill(x + DIVIDER_X, y + 16, x + DIVIDER_X + 1, y + 144, DIVIDER);

        // Group trays behind each category, so the bays read as a built machine.
        group(g, x + 4, y + 23, 40, 23);   // board + PSU
        group(g, x + 4, y + 53, 40, 60);   // disks (3 rows of 2)
        group(g, x + 48, y + 23, 76, 23);  // CPUs
        group(g, x + 48, y + 53, 76, 42);  // RAM (2 rows of 4)
        group(g, x + 48, y + 101, 58, 42); // GPUs (2 rows of 3)

        slot(g, x + 8, y + 28);
        slot(g, x + 26, y + 28);
        for (int i = 0; i < 6; i++) {
            slot(g, x + 8 + (i % 2) * 18, y + 58 + (i / 2) * 18);
        }
        for (int i = 0; i < 4; i++) {
            slot(g, x + 52 + i * 18, y + 28);
        }
        for (int i = 0; i < 8; i++) {
            slot(g, x + 52 + (i % 4) * 18, y + 58 + (i / 4) * 18);
        }
        for (int i = 0; i < 6; i++) {
            slot(g, x + 52 + (i % 3) * 18, y + 106 + (i / 3) * 18);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + 160 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + 218);
        }
    }

    private static void group(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, GROUP_BORDER);
        g.fill(x, y, x + w, y + h, GROUP_FILL);
    }

    private static void slot(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, "Board", 8, 17, LABEL, false);
        g.drawString(font, "Storage", 8, 47, LABEL, false);
        g.drawString(font, "CPU", 52, 17, LABEL, false);
        g.drawString(font, "Memory", 52, 47, LABEL, false);
        g.drawString(font, "Graphics", 52, 95, LABEL, false);

        renderSummary(g);
    }

    private void renderSummary(final GuiGraphics g) {
        final ComputerBuild build = menu.currentBuild();
        final String status;
        final int color;
        if (build == null) {
            status = "INCOMPLETE";
            color = RED;
        } else if (!build.validate().valid() || build.rams().isEmpty()) {
            status = "CHECK";
            color = YELLOW;
        } else {
            status = "READY";
            color = GREEN;
        }
        g.drawString(font, status, PANEL_X, 20, color, false);

        final long capacity = build == null ? 0L : build.totalCapacity();
        final int queues = build == null ? 0 : build.parallelQueues();
        final long ram = build == null ? 0L : build.ramBuffer();
        final long storage = build == null ? 0L : build.storageMb();
        g.drawString(font, fmt.compact(capacity, Unit.IT_PER_TICK), PANEL_X, 36, LABEL, false);
        g.drawString(font, queues + " queue(s)", PANEL_X, 47, LABEL, false);
        g.drawString(font, ram + " RAM", PANEL_X, 58, LABEL, false);
        g.drawString(font, fmt.compact(storage, Unit.MB), PANEL_X, 69, LABEL, false);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
