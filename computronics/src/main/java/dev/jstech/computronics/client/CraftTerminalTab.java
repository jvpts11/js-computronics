/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.ComputerTerminalMenu;
import dev.jstech.computronics.operation.payload.CraftCatalogPayload;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The Craft tab: a scrollable catalog grid of craftable patterns plus running/recent craft rows.
 */
final class CraftTerminalTab extends AbstractTerminalTab {

    // Layout constants — mirror ComputerTerminalScreen; update together if layout changes.
    private static final int NET_X = 68;
    private static final int CRAFT_COLS = 9;
    private static final int CRAFT_ROWS = 2;
    private static final int CRAFT_GRID_Y = 40;
    private static final int CRAFT_RUNNING_Y = 82;
    private static final int CRAFT_RECENT_Y = 116;

    CraftTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        super(screen, menu);
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        final var catalog = menu.craftCatalog();
        final int maxScroll = Math.max(0, (catalog.size() + CRAFT_COLS - 1) / CRAFT_COLS - CRAFT_ROWS);
        screen.craftScroll = Math.max(0, Math.min(screen.craftScroll, maxScroll));
        for (int row = 0; row < CRAFT_ROWS; row++) {
            for (int col = 0; col < CRAFT_COLS; col++) {
                final int sx = x + NET_X + col * 18;
                final int sy = y + CRAFT_GRID_Y + row * 18;
                slotBg(g, sx, sy);
                final int index = (row + screen.craftScroll) * CRAFT_COLS + col;
                if (index < catalog.size()) {
                    final var entry = catalog.get(index);
                    drawDataIcon(g, StorageKey.of(entry.result()), -1L, sx, sy);
                    final int dot = switch (entry.availability()) {
                        case CraftCatalogPayload.DOT_GREEN -> GREEN();
                        case CraftCatalogPayload.DOT_AMBER -> AMBER();
                        default -> RED();
                    };
                    g.fill(sx + 13, sy + 1, sx + 17, sy + 5, dot);
                }
            }
        }
        // RUNNING rows: panel strip + progress bar.
        final var running = runningCrafts();
        for (int i = 0; i < Math.min(2, running.size()); i++) {
            final OperationRecord op = running.get(i);
            final int ry = y + CRAFT_RUNNING_Y + 9 + i * 12;
            g.fill(x + NET_X, ry, x + NET_X + CRAFT_COLS * 18, ry + 10, PANEL());
            final int barX = x + NET_X + 92;
            final int barW = 56;
            g.fill(barX, ry + 3, barX + barW, ry + 7, TRACK());
            final int pct = op.requested() <= 0 ? 0
                    : (int) Math.min(100, op.moved() * 100 / Math.max(1, op.requested()));
            g.fill(barX, ry + 3, barX + barW * pct / 100, ry + 7, ACCENT());
        }
        // RECENT rows: plain strips (status text is drawn in renderTabLabels).
        final var recent = recentCrafts();
        for (int i = 0; i < Math.min(recentRows(), recent.size()); i++) {
            final int ry = y + CRAFT_RECENT_Y + 9 + i * 10;
            g.fill(x + NET_X, ry, x + NET_X + CRAFT_COLS * 18, ry + 9, PANEL());
        }
    }

    @Override
    public void renderTabLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        // Note: this method uses screen-relative Y constants directly (CRAFT_GRID_Y, etc.),
        // matching the original craftLabels which didn't use the cy parameter.
        final var catalog = menu.craftCatalog();
        g.drawString(font(), "CRAFTABLE", NET_X, 30, DIM(), false);
        g.drawString(font(), catalog.size() + (catalog.size() == 1 ? " pattern" : " patterns"),
                NET_X + 64, 30, TEXT(), false);
        if (catalog.isEmpty()) {
            g.drawString(font(), "no patterns loaded - use a Pattern Reader", NET_X, CRAFT_GRID_Y + 6, DIM(), false);
        }
        final int totalRows = (catalog.size() + CRAFT_COLS - 1) / CRAFT_COLS;
        if (totalRows > CRAFT_ROWS) {
            g.drawString(font(), (screen.craftScroll + 1) + "/" + (totalRows - CRAFT_ROWS + 1),
                    NET_X + CRAFT_COLS * 18 - 24, 30, DIM(), false);
        }
        g.drawString(font(), "RUNNING", NET_X, CRAFT_RUNNING_Y, DIM(), false);
        final var running = runningCrafts();
        if (running.isEmpty()) {
            g.drawString(font(), "-", NET_X + 48, CRAFT_RUNNING_Y, DIM(), false);
        }
        for (int i = 0; i < Math.min(2, running.size()); i++) {
            final OperationRecord op = running.get(i);
            final int ry = CRAFT_RUNNING_Y + 10 + i * 12;
            g.drawString(font(), trim(op.name().getString(), 9), NET_X + 3, ry, TEXT(), false);
            g.drawString(font(), fmt(op.moved()) + "/" + fmt(op.requested()), NET_X + 56, ry, DIM(), false);
        }
        g.drawString(font(), "RECENT", NET_X, CRAFT_RECENT_Y, DIM(), false);
        final var recent = recentCrafts();
        if (recent.isEmpty()) {
            g.drawString(font(), "-", NET_X + 44, CRAFT_RECENT_Y, DIM(), false);
        }
        for (int i = 0; i < Math.min(recentRows(), recent.size()); i++) {
            final OperationRecord op = recent.get(i);
            final int ry = CRAFT_RECENT_Y + 10 + i * 10;
            g.drawString(font(), trim(op.name().getString(), 11) + " x" + fmt(op.moved()),
                    NET_X + 3, ry, TEXT(), false);
            final String st = switch (op.status()) {
                case OperationRecord.STATUS_COMPLETED -> "COMPLETED";
                case OperationRecord.STATUS_PARTIAL -> "PARTIAL";
                case OperationRecord.STATUS_RESOURCE_LOCKED -> "LOCKED";
                case OperationRecord.STATUS_DISCARDED -> "DISCARDED";
                default -> "FAILED";
            };
            final int color = switch (op.status()) {
                case OperationRecord.STATUS_COMPLETED -> GREEN();
                case OperationRecord.STATUS_PARTIAL -> AMBER();
                case OperationRecord.STATUS_DISCARDED -> DIM();
                default -> RED();
            };
            g.drawString(font(), st, NET_X + CRAFT_COLS * 18 - font().width(st) - 3, ry, color, false);
        }
    }

    private List<OperationRecord> runningCrafts() {
        final List<OperationRecord> out = new ArrayList<>();
        for (final OperationRecord op : menu.activeOps()) {
            if (op.type() == OperationRecord.TYPE_CRAFT) {
                out.add(op);
            }
        }
        return out;
    }

    private List<OperationRecord> recentCrafts() {
        final List<OperationRecord> out = new ArrayList<>();
        for (final OperationRecord op : menu.operationsLog()) {
            if (op.type() == OperationRecord.TYPE_CRAFT) {
                out.add(op);
            }
        }
        return out;
    }

    private int recentRows() {
        return menu.mainframeHost() ? 3 : 2;
    }

    private static String trim(final String s, final int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
