/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Base for all {@link TerminalTab} implementations. Holds the screen and menu references that
 * every tab needs, and provides named accessors for palette colours and the shared drawing
 * helpers that live on the parent screen.
 */
abstract class AbstractTerminalTab implements TerminalTab {

    protected final ComputerTerminalScreen screen;
    protected final ComputerTerminalMenu menu;

    AbstractTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        this.screen = screen;
        this.menu = menu;
    }

    // ---- Font + position accessors -------------------------------------------------------

    protected Font font() {
        return screen.tabFont();
    }

    // ---- Palette -------------------------------------------------------------------------
    // Delegating to JscOsTheme is semantically identical to reading the cached palette fields
    // on the screen: the era skin is bound for the entire render pass, so the values match.

    protected int OUTER() { return JscOsTheme.outer(); }
    protected int SCREEN_COL() { return JscOsTheme.screen(); }
    protected int RAIL() { return JscOsTheme.rail(); }
    protected int PANEL() { return JscOsTheme.panel(); }
    protected int LINE() { return JscOsTheme.line(); }
    protected int TRACK() { return JscOsTheme.track(); }
    protected int SLOT_BG() { return JscOsTheme.slotBg(); }
    protected int SLOT_EDGE() { return JscOsTheme.slotEdge(); }
    protected int ACCENT() { return JscOsTheme.accent(); }
    protected int ACCENT2() { return JscOsTheme.accent2(); }
    protected int GREEN() { return JscOsTheme.green(); }
    protected int AMBER() { return JscOsTheme.amber(); }
    protected int RED() { return JscOsTheme.red(); }
    protected int TEXT() { return JscOsTheme.text(); }
    protected int DIM() { return JscOsTheme.dim(); }
    protected int TAB_ON() { return JscOsTheme.tabOn(); }
    protected int HOVER() { return JscOsTheme.hover(); }

    // ---- Shared rendering helpers (delegating to package-private methods on the screen) ---

    protected void slotBg(final GuiGraphics g, final int x, final int y) {
        screen.slotBg(g, x, y);
    }

    protected void gridBg(final GuiGraphics g, final int x, final int y, final int dy, final int rows) {
        screen.gridBg(g, x, y, dy, rows);
    }

    protected void inlineTrack(final GuiGraphics g, final int x, final int y, final int w,
                               final double f, final int color) {
        screen.inlineTrack(g, x, y, w, f, color);
    }

    protected void track(final GuiGraphics g, final int x, final int y, final int w,
                         final double f, final int color) {
        screen.track(g, x, y, w, f, color);
    }

    protected void tile(final GuiGraphics g, final int x, final int y, final String key,
                        final String val, final String unit) {
        screen.tile(g, x, y, key, val, unit);
    }

    protected void barLabel(final GuiGraphics g, final int x, final int y, final int w,
                            final String key, final String val) {
        screen.barLabel(g, x, y, w, key, val);
    }

    protected void drawDataIcon(final GuiGraphics g,
                                final dev.jsc.jscomputronics.module.computing.storage.StorageKey key,
                                final long count, final int x, final int y) {
        screen.drawDataIcon(g, key, count, x, y);
    }

    protected void moveRow(final GuiGraphics g, final int cx, final int my,
                           final dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.MoveRow mv) {
        screen.moveRow(g, cx, my, mv);
    }

    // ---- Shared query helpers ------------------------------------------------------------

    protected java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry> visibleItems() {
        return screen.visibleItems();
    }

    protected static double frac(final int a, final int b) {
        return b <= 0 ? 0 : Math.min(1.0, (double) a / b);
    }

    protected static String fmt(final long v) {
        return ComputerTerminalScreen.fmt(v);
    }

    protected static String statusLabel(final byte status) {
        return ComputerTerminalScreen.statusLabel(status);
    }

    protected int statusColor(final byte status) {
        return screen.statusColor(status);
    }

    // ---- Additional shared helpers -------------------------------------------------------

    protected static String opTypeLabel(final byte type) {
        return switch (type) {
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_INSERT -> "INSERT";
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_DELETE -> "DELETE";
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_MOVE -> "MOVE";
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_ANALYZE -> "ANALYZE";
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_REINDEX -> "REINDEX";
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_VACUUM -> "VACUUM";
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_DROP -> "DROP";
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_CRAFT -> "CRAFT";
            default -> "SELECT";
        };
    }

    protected int opTypeColor(final byte type) {
        return switch (type) {
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_INSERT,
                    dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_CRAFT -> AMBER();
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_DELETE,
                    dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_DROP -> RED();
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_MOVE -> GREEN();
            case dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_ANALYZE,
                    dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_REINDEX,
                    dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord.TYPE_VACUUM -> DIM();
            default -> ACCENT2();
        };
    }

    protected int clampOpScroll(final int size) {
        return screen.clampOpScroll(size);
    }

    protected int sliderValue(final int disk) {
        return screen.sliderValue(disk);
    }

    protected static boolean inRect(final double mx, final double my,
                                    final int x, final int y, final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
