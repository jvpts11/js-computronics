/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.theme;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One immutable GUI skin: a {@link EraPalette} plus the {@link EraStyle} overlay flags, with the palette-coupled
 * drawing helpers that used to be the {@code static} methods on {@code JscOsTheme}. A computing screen resolves the
 * theme of its host computer's {@code HardwareEra} and paints through it, so the same screen code renders in the
 * skin of whatever era it is running on.
 *
 * <p>The STANDARD theme's style is {@linkplain EraStyle#flat flat} (every overlay off), so its helpers execute the
 * identical {@code g.fill(...)} sequence the original {@code JscOsTheme} did — byte-for-byte the same pixels.
 */
public final class EraTheme {

    private final EraPalette p;
    private final EraStyle s;

    public EraTheme(final EraPalette palette, final EraStyle style) {
        this.p = palette;
        this.s = style;
    }

    public EraPalette palette() {
        return p;
    }

    public EraStyle style() {
        return s;
    }

    // Semantic color accessors (these replace the former public static final int constants).

    public int outer() {
        return p.outer();
    }

    public int screen() {
        return p.screen();
    }

    public int rail() {
        return p.rail();
    }

    public int panel() {
        return p.panel();
    }

    public int line() {
        return p.line();
    }

    public int track() {
        return p.track();
    }

    public int slotBg() {
        return p.slotBg();
    }

    public int slotEdge() {
        return p.slotEdge();
    }

    public int accent() {
        return p.accent();
    }

    public int accent2() {
        return p.accent2();
    }

    public int green() {
        return p.green();
    }

    public int amber() {
        return p.amber();
    }

    public int red() {
        return p.red();
    }

    public int text() {
        return p.text();
    }

    public int dim() {
        return p.dim();
    }

    public int tabOn() {
        return p.tabOn();
    }

    public int tabLabelOn() {
        return p.tabLabelOn();
    }

    public int hover() {
        return p.hover();
    }

    public float small() {
        return s.fontScaleSmall();
    }

    // Palette-coupled drawing. With a flat style these are the exact sequences the original helpers ran.

    public void window(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, p.outer());
        g.fill(x, y, x + w, y + h, p.screen());
        // Double-bevel themes (e.g., Legacy) draw a raised 3D frame just inside the outer border,
        // giving the window the "dialog box" look authentic to the era.
        bevel(g, x, y, w, h, true);
        if (s.scanlines() && s.scanlineColor() != 0) {
            // Square, low-alpha 1px lines every 2px across the screen rect — a CRT scanline finish.
            for (int ly = y + 1; ly < y + h; ly += 2) {
                g.fill(x, ly, x + w, ly + 1, s.scanlineColor());
            }
        }
    }

    public void slot(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, p.slotEdge());
        g.fill(x, y, x + 16, y + 16, p.slotBg());
        bevel(g, x, y, 18, 18, true);
    }

    public void panel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + h, p.panel());
        g.fill(x, y, x + w, y + 1, p.line());
        // Panels use a sunken bevel (inverted edges) on double-bevel themes to convey a recessed display area.
        bevelSunken(g, x, y, w, h);
    }

    public void hLine(final GuiGraphics g, final int x, final int y, final int w) {
        g.fill(x, y, x + w, y + 1, p.line());
    }

    public void vLine(final GuiGraphics g, final int x, final int y, final int h) {
        g.fill(x, y, x + 1, y + h, p.line());
    }

    public void headerBar(final GuiGraphics g, final int cx, final int cy, final int cw) {
        g.fill(cx, cy, cx + cw, cy + 16, p.panel());
        g.fill(cx, cy + 16, cx + cw, cy + 17, p.line());
    }

    public void button(final GuiGraphics g, final int x, final int y, final int w, final int h,
                       final boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? p.hover() : p.panel());
        g.fill(x, y, x + w, y + 1, p.line());
        bevel(g, x, y, w, h, !hovered);
    }

    public void track(final GuiGraphics g, final int x, final int y, final int w,
                      final double frac, final int fillColor) {
        g.fill(x, y, x + w, y + 7, p.track());
        g.fill(x, y, x + w, y + 1, p.line());
        final int fw = (int) Math.round((w - 2) * Math.max(0.0, Math.min(1.0, frac)));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + 6, fillColor);
            if (s.glowAccent() && s.glowColor() != 0 && fillColor == p.accent()) {
                g.fill(x + 1, y, x + 1 + fw, y + 1, s.glowColor());
                g.fill(x + 1, y + 6, x + 1 + fw, y + 7, s.glowColor());
            }
        }
    }

    /**
     * Draws the optional raised double bevel on a rectangle: a 1px light top-left edge and a 1px dark
     * bottom-right edge. A no-op unless the style enables it and supplies edge colors, so STANDARD never
     * bevels and output is byte-identical to before.
     */
    private void bevel(final GuiGraphics g, final int x, final int y, final int w, final int h,
                       final boolean raised) {
        if (!s.doubleBevel() || !raised) {
            return;
        }
        if (s.bevelLight() != 0) {
            g.fill(x, y, x + w, y + 1, s.bevelLight());
            g.fill(x, y, x + 1, y + h, s.bevelLight());
        }
        if (s.bevelDark() != 0) {
            g.fill(x, y + h - 1, x + w, y + h, s.bevelDark());
            g.fill(x + w - 1, y, x + w, y + h, s.bevelDark());
        }
    }

    /**
     * Draws the optional sunken double bevel: dark top-left and light bottom-right, giving a recessed
     * appearance suitable for panels and display areas. A no-op on flat-style themes.
     */
    private void bevelSunken(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        if (!s.doubleBevel()) {
            return;
        }
        if (s.bevelDark() != 0) {
            g.fill(x, y, x + w, y + 1, s.bevelDark());
            g.fill(x, y, x + 1, y + h, s.bevelDark());
        }
        if (s.bevelLight() != 0) {
            g.fill(x, y + h - 1, x + w, y + h, s.bevelLight());
            g.fill(x + w - 1, y, x + w, y + h, s.bevelLight());
        }
    }
}
