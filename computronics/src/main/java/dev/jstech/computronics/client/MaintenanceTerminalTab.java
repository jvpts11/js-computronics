/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.ComputerTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Maintenance tab (Mainframe-only): index stats and the ANALYZE / VACUUM / REINDEX / DROP actions.
 */
final class MaintenanceTerminalTab extends AbstractTerminalTab {

    // Layout constants, mirroring ComputerTerminalScreen; update together if layout changes.
    private static final int MNT_TILE_ROW1_Y = 32;
    private static final int MNT_TILE_ROW2_Y = 56;
    private static final int MNT_TILE_H = 22;
    private static final int MNT_ACTIONS_Y = 82;
    /*
     * The health strip takes the ACTIONS caption's line when the index needs attention: the state of
     * the index is worth more than a decorative label, and the layout below stays where it was.
     */
    private static final int MNT_HEALTH_H = 10;
    private static final int MNT_BTN_ROW1_Y = 94;
    private static final int MNT_BTN_REINDEX_Y = 112;
    private static final int MNT_BTN_DROP_Y = 130;
    private static final int MNT_BTN_H = 15;

    MaintenanceTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        super(screen, menu);
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        final int tileW = (cw - 4) / 2;
        for (int r = 0; r < 2; r++) {
            final int ty = cy + (r == 0 ? MNT_TILE_ROW1_Y : MNT_TILE_ROW2_Y);
            for (int col = 0; col < 2; col++) {
                final int tx = cx + col * (tileW + 4);
                g.fill(tx, ty, tx + tileW, ty + MNT_TILE_H, PANEL());
                g.fill(tx, ty, tx + tileW, ty + 1, LINE());
            }
        }
        final var health = menu.indexHealth();
        if (health != dev.jstech.computronics.operation.index.IndexHealth.State.OK) {
            final int stripY = cy + MNT_ACTIONS_Y - 1;
            final int base = health == dev.jstech.computronics.operation.index.IndexHealth.State.FRAGMENTED
                    ? 0xFF7A3A14 : 0xFF6E5A16;
            g.fill(cx, stripY, cx + cw, stripY + MNT_HEALTH_H, base);
            g.fill(cx, stripY, cx + cw, stripY + 1, 0x55FFFFFF);
        }
        final int halfW = (cw - 4) / 2;
        maintBtnBg(g, mouseX, mouseY, cx, cy + MNT_BTN_ROW1_Y, halfW, 0xFF1C6F86, 0xFF2A93AE);
        maintBtnBg(g, mouseX, mouseY, cx + halfW + 4, cy + MNT_BTN_ROW1_Y, halfW, 0xFF1C6F86, 0xFF2A93AE);
        maintBtnBg(g, mouseX, mouseY, cx, cy + MNT_BTN_REINDEX_Y, cw, 0xFF7A5A1E, 0xFFA8801F);
        maintBtnBg(g, mouseX, mouseY, cx, cy + MNT_BTN_DROP_Y, cw, 0xFF7A241C, 0xFFB23228);
    }

    @Override
    public void renderTabLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        g.drawString(font(), "STORAGE INDEX", cx, cy + 20, DIM(), false);
        final int tileW = (cw - 4) / 2;
        tile(g, cx, cy + MNT_TILE_ROW1_Y, "TYPES", fmt(menu.indexedTypes()), "");
        tile(g, cx + tileW + 4, cy + MNT_TILE_ROW1_Y, "SERVERS", String.valueOf(menu.indexedServers()), "");
        tile(g, cx, cy + MNT_TILE_ROW2_Y, "LOCKS", String.valueOf(menu.activeLocks()), "");
        final long used = menu.networkStorageUsed();
        final long total = menu.networkStorageTotal();
        tile(g, cx + tileW + 4, cy + MNT_TILE_ROW2_Y, "STORAGE",
                total <= 0 ? "0" : fmt(used) + "/" + fmt(total), "");
        final var health = menu.indexHealth();
        if (health == dev.jstech.computronics.operation.index.IndexHealth.State.OK) {
            g.drawString(font(), "ACTIONS", cx, cy + MNT_ACTIONS_Y, DIM(), false);
        } else {
            // Name the state, how many item types are in doubt, and the run that settles it.
            final int types = menu.indexHealthTypes();
            final String action = health
                    == dev.jstech.computronics.operation.index.IndexHealth.State.FRAGMENTED
                    ? "VACUUM" : "REINDEX";
            g.drawString(font(), health.name() + " - " + types + " item type"
                    + (types == 1 ? "" : "s") + " affected", cx + 2, cy + MNT_ACTIONS_Y, 0xFFFFFFFF, false);
            final String hint = "run " + action;
            g.drawString(font(), hint, cx + cw - 2 - font().width(hint), cy + MNT_ACTIONS_Y,
                    0xFFFFE0A0, false);
        }
        final int halfW = (cw - 4) / 2;
        g.drawCenteredString(font(), "ANALYZE", cx + halfW / 2, cy + MNT_BTN_ROW1_Y + 4, 0xFFFFFFFF);
        g.drawCenteredString(font(), "VACUUM", cx + halfW + 4 + halfW / 2, cy + MNT_BTN_ROW1_Y + 4, 0xFFFFFFFF);
        g.drawCenteredString(font(), "REINDEX", cx + cw / 2, cy + MNT_BTN_REINDEX_Y + 4, 0xFFFFFFFF);
        g.drawCenteredString(font(), "DROP DATA...", cx + cw / 2, cy + MNT_BTN_DROP_Y + 4, 0xFFFFFFFF);
        if (!screen.maintHint.isEmpty()) {
            g.drawString(font(), screen.maintHint, cx, cy + MNT_BTN_DROP_Y + MNT_BTN_H + 2, ACCENT(), false);
        }
    }

    private void maintBtnBg(final GuiGraphics g, final int mx, final int my,
                            final int bx, final int by, final int w,
                            final int base, final int hover) {
        final boolean hov = inRect(mx, my, bx, by, w, MNT_BTN_H);
        g.fill(bx, by, bx + w, by + MNT_BTN_H, hov ? hover : base);
        g.fill(bx, by, bx + w, by + 1, 0x33FFFFFF);
    }
}
