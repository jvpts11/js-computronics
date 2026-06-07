/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared flat-dark "computer OS" theme for the computing block GUIs (Mainframe, Personal Computer, Server Assembly, Server Rack), matching the Monitor terminal: square corners only, flat fills, a cyan accent, 1px separator lines and slot cells with a 1px lighter edge.
 */
public final class JscOsTheme {

    private JscOsTheme() {
    }

    // Flat palette (ARGB). No rounded corners anywhere.
    public static final int OUTER = 0xFF05070A;
    public static final int SCREEN = 0xFF0B0E13;
    public static final int RAIL = 0xFF0E131A;
    public static final int PANEL = 0xFF11161D;
    public static final int LINE = 0xFF1D2530;
    public static final int TRACK = 0xFF0A0E14;
    public static final int SLOT_BG = 0xFF0A0D12;
    public static final int SLOT_EDGE = 0xFF1C2531;
    public static final int ACCENT = 0xFF39D6C4;
    public static final int ACCENT2 = 0xFF2AA7E0;
    public static final int GREEN = 0xFF5FE07A;
    public static final int AMBER = 0xFFF0B23A;
    public static final int RED = 0xFFEF6A5A;
    public static final int TEXT = 0xFFCDD6E2;
    public static final int DIM = 0xFF7D8A9C;
    public static final int TAB_ON = 0xFF15212A;
    public static final int HOVER = 0xFF1A2937;

    // Backgrounds (renderBg, absolute coordinates)

    public static void window(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, OUTER);
        g.fill(x, y, x + w, y + h, SCREEN);
    }

    public static void slot(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    public static void panel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, LINE);
    }

    public static void hLine(final GuiGraphics g, final int x, final int y, final int w) {
        g.fill(x, y, x + w, y + 1, LINE);
    }

    public static void vLine(final GuiGraphics g, final int x, final int y, final int h) {
        g.fill(x, y, x + 1, y + h, LINE);
    }

    public static void headerBar(final GuiGraphics g, final int cx, final int cy, final int cw) {
        g.fill(cx, cy, cx + cw, cy + 16, PANEL);
        g.fill(cx, cy + 16, cx + cw, cy + 17, LINE);
    }

    public static void button(final GuiGraphics g, final int x, final int y, final int w, final int h,
                              final boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? HOVER : PANEL);
        g.fill(x, y, x + w, y + 1, LINE);
    }

    public static void track(final GuiGraphics g, final int x, final int y, final int w,
                             final double frac, final int fillColor) {
        g.fill(x, y, x + w, y + 7, TRACK);
        g.fill(x, y, x + w, y + 1, LINE);
        final int fw = (int) Math.round((w - 2) * Math.max(0.0, Math.min(1.0, frac)));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + 6, fillColor);
        }
    }

    // Text (renderLabels, GUI-relative coordinates)

    public static void text(final GuiGraphics g, final Font f, final String s, final int x, final int y,
                            final int color) {
        g.drawString(f, s, x, y, color, false);
    }

    public static void textRight(final GuiGraphics g, final Font f, final String s, final int xRight,
                                 final int y, final int color) {
        g.drawString(f, s, xRight - f.width(s), y, color, false);
    }

    public static void textCenter(final GuiGraphics g, final Font f, final String s, final int cx,
                                  final int y, final int color) {
        g.drawCenteredString(f, s, cx, y, color);
    }

    public static final float SMALL = 0.75f;

    public static int widthS(final Font f, final String s) {
        return (int) Math.ceil(f.width(s) * SMALL);
    }

    public static void textS(final GuiGraphics g, final Font f, final String s, final int x, final int y,
                             final int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SMALL, SMALL, 1.0f);
        g.drawString(f, s, 0, 0, color, false);
        g.pose().popPose();
    }

    public static void textSRight(final GuiGraphics g, final Font f, final String s, final int xRight,
                                  final int y, final int color) {
        textS(g, f, s, xRight - widthS(f, s), y, color);
    }

    public static void textSCenter(final GuiGraphics g, final Font f, final String s, final int cx,
                                   final int y, final int color) {
        textS(g, f, s, cx - widthS(f, s) / 2, y, color);
    }

    public static void tileTextS(final GuiGraphics g, final Font f, final int x, final int y,
                                 final String key, final String value, final int valueColor) {
        textS(g, f, key, x + 3, y + 3, DIM);
        textS(g, f, value, x + 3, y + 11, valueColor);
    }

    public static void tileText(final GuiGraphics g, final Font f, final int x, final int y,
                                final String key, final String value, final String unit, final int valueColor) {
        g.drawString(f, key, x + 4, y + 4, DIM, false);
        g.drawString(f, value, x + 4, y + 13, valueColor, false);
        if (unit != null && !unit.isEmpty()) {
            g.drawString(f, unit, x + 6 + f.width(value), y + 15, DIM, false);
        }
    }

    public static String fmt(final long n) {
        if (n < 10_000L) {
            return String.format("%,d", n);
        }
        if (n < 1_000_000L) {
            return String.format("%.1fk", n / 1_000.0);
        }
        return String.format("%.1fM", n / 1_000_000.0);
    }
}
