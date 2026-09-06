/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared placeholder drawing for the Industrial machine screens.
 */
final class MachineScreenSupport {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int ENERGY_EMPTY = 0xFF200000;
    private static final int ENERGY_FULL = 0xFFFF3030;

    private MachineScreenSupport() {
    }

    static void drawPanel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, BEVEL_LIGHT);
        g.fill(x, y, x + 1, y + h, BEVEL_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, BEVEL_DARK);
        g.fill(x + w - 1, y, x + w, y + h, BEVEL_DARK);
    }

    static void drawSlot(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    static void drawPlayerInventory(final GuiGraphics g, final int left, final int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, left + 8 + col * 18, top + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(g, left + 8 + col * 18, top + 142);
        }
    }

    static void drawEnergyBar(final GuiGraphics g, final int x, final int y, final int w, final int h,
                              final int energy, final int maxEnergy) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_BORDER);
        g.fill(x, y, x + w, y + h, ENERGY_EMPTY);
        if (maxEnergy > 0 && energy > 0) {
            final int filled = Math.min(h, energy * h / maxEnergy);
            g.fill(x, y + h - filled, x + w, y + h, ENERGY_FULL);
        }
    }

    static void drawProgressBar(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                final int progress, final int maxProgress, final int fillColor) {
        g.fill(x, y, x + w, y + h, SLOT_FILL);
        if (maxProgress > 0 && progress > 0) {
            final int filled = Math.min(w, progress * w / maxProgress);
            g.fill(x, y, x + filled, y + h, fillColor);
        }
    }
}
