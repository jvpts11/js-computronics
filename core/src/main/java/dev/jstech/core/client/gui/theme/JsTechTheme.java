/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Render-scoped facade over the active {@link EraTheme} for the computing block GUIs (Mainframe, Personal Computer,
 * Server Assembly, Server Rack, and the program/terminal screens). It keeps the original square-cornered "computer
 * OS" look as its STANDARD default and lets each screen repaint in the skin of its host computer's hardware era.
 *
 * <p>A screen binds its resolved theme once per render pass ({@link #bind}) and unbinds afterwards; every color
 * accessor and palette-coupled helper below reads the bound theme. With the STANDARD theme bound (the default, and
 * the fallback for any screen without a valid build), the helpers run the exact fill sequences the flat-dark theme
 * always did, so the default GUI is byte-identical to before.
 */
public final class JsTechTheme {

    private JsTechTheme() {
    }

    private static EraTheme active = EraThemes.STANDARD;

    /** Binds the theme for the current render pass. The screen base calls this at the top of {@code render}. */
    public static void bind(final EraTheme theme) {
        active = theme == null ? EraThemes.STANDARD : theme;
    }

    /** Restores the STANDARD default after a render pass, so any unthemed draw stays on the frozen skin. */
    public static void unbind() {
        active = EraThemes.STANDARD;
    }

    /** The theme currently bound (STANDARD by default). */
    public static EraTheme active() {
        return active;
    }

    // Palette accessors (these read the bound theme; they replaced the former public static final int constants).

    public static int outer() {
        return active.outer();
    }

    public static int screen() {
        return active.screen();
    }

    public static int rail() {
        return active.rail();
    }

    public static int panel() {
        return active.panel();
    }

    public static int line() {
        return active.line();
    }

    public static int track() {
        return active.track();
    }

    public static int slotBg() {
        return active.slotBg();
    }

    public static int slotEdge() {
        return active.slotEdge();
    }

    public static int accent() {
        return active.accent();
    }

    public static int accent2() {
        return active.accent2();
    }

    public static int green() {
        return active.green();
    }

    public static int amber() {
        return active.amber();
    }

    public static int red() {
        return active.red();
    }

    public static int text() {
        return active.text();
    }

    public static int dim() {
        return active.dim();
    }

    public static int tabOn() {
        return active.tabOn();
    }

    public static int tabLabelOn() {
        return active.tabLabelOn();
    }

    public static int hover() {
        return active.hover();
    }

    public static float small() {
        return active.small();
    }

    // Backgrounds (renderBg, absolute coordinates): delegate to the bound theme's palette-coupled drawing.

    public static void window(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        active.window(g, x, y, w, h);
    }

    public static void slot(final GuiGraphics g, final int x, final int y) {
        active.slot(g, x, y);
    }

    public static void panel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        active.panel(g, x, y, w, h);
    }

    public static void hLine(final GuiGraphics g, final int x, final int y, final int w) {
        active.hLine(g, x, y, w);
    }

    public static void vLine(final GuiGraphics g, final int x, final int y, final int h) {
        active.vLine(g, x, y, h);
    }

    public static void headerBar(final GuiGraphics g, final int cx, final int cy, final int cw) {
        active.headerBar(g, cx, cy, cw);
    }

    public static void button(final GuiGraphics g, final int x, final int y, final int w, final int h,
                              final boolean hovered) {
        active.button(g, x, y, w, h, hovered);
    }

    public static void track(final GuiGraphics g, final int x, final int y, final int w,
                             final double frac, final int fillColor) {
        active.track(g, x, y, w, frac, fillColor);
    }

    // Text (renderLabels, GUI-relative coordinates): palette-agnostic; the caller passes the color.

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

    public static int widthS(final Font f, final String s) {
        return (int) Math.ceil(f.width(s) * active.small());
    }

    public static void textS(final GuiGraphics g, final Font f, final String s, final int x, final int y,
                             final int color) {
        final float scale = active.small();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
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
        textS(g, f, key, x + 3, y + 3, active.dim());
        textS(g, f, value, x + 3, y + 11, valueColor);
    }

    public static void tileText(final GuiGraphics g, final Font f, final int x, final int y,
                                final String key, final String value, final String unit, final int valueColor) {
        g.drawString(f, key, x + 4, y + 4, active.dim(), false);
        g.drawString(f, value, x + 4, y + 13, valueColor, false);
        if (unit != null && !unit.isEmpty()) {
            g.drawString(f, unit, x + 6 + f.width(value), y + 15, active.dim(), false);
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
