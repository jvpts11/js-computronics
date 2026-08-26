/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws a small icon for each desktop program (Files, Terminal, Editor, Network, This PC). Each
 * Panes version draws genuinely different artwork from the same concept — not a recolour:
 * <ul>
 *   <li><b>95</b> — chunky, flat 16-colour shapes with a hard dark outline (the raised CRT look).</li>
 *   <li><b>XP</b> — larger, glossy, with a gradient body and a bright highlight (Luna).</li>
 *   <li><b>11</b> — flat, geometric, two-tone, no hard outline (Fluent).</li>
 * </ul>
 * Built from proportional fills so they scale to any box and keep sharp, square edges.
 */
public final class ProgramIcons {

    private ProgramIcons() {
    }

    /** Draws an icon with the classic (Panes 95) artwork. */
    public static void draw(final GuiGraphics g, final int x, final int y, final int w, final int h,
                            final String label) {
        draw(g, x, y, w, h, label, "panes_95");
    }

    /** Draws an icon in the artwork style of the given OS id ({@code panes_95/xp/11}). */
    public static void draw(final GuiGraphics g, final int x, final int y, final int w, final int h,
                            final String label, final String os) {
        switch (label) {
            case "Files" -> folder(g, x, y, w, h, os);
            case "Terminal", "Shell" -> terminal(g, x, y, w, h, os);
            case "Editor" -> editor(g, x, y, w, h, os);
            case "Network" -> network(g, x, y, w, h, os);
            case "This PC" -> thisPc(g, x, y, w, h, os);
            case "NMS" -> nms(g, x, y, w, h, os);
            default -> generic(g, x, y, w, h, os);
        }
    }

    // -------------------------------------------------------------------------
    // Icons — each switches its whole drawing per OS, not just colour.
    // -------------------------------------------------------------------------

    private static void folder(final GuiGraphics g, final int x, final int y, final int w, final int h,
                               final String os) {
        switch (os) {
            case "panes_xp" -> {
                box(g, x, y, w, h, 0.06, 0.26, 0.52, 0.40, 0xFFE8A93A);          // back tab
                grad(g, x, y, w, h, 0.06, 0.34, 0.94, 0.86, 0xFFFFE08A, 0xFFE89A1E); // glossy body
                box(g, x, y, w, h, 0.06, 0.34, 0.94, 0.50, 0x55FFFFFF);          // top gloss
                frame(g, x, y, w, h, 0.06, 0.26, 0.94, 0.86, 0xFFB5760E);
            }
            case "panes_11" -> {
                box(g, x, y, w, h, 0.10, 0.24, 0.60, 0.36, 0xFFFFD27A);          // tab, lighter
                box(g, x, y, w, h, 0.06, 0.32, 0.94, 0.84, 0xFFF4A53A);          // back leaf
                box(g, x, y, w, h, 0.06, 0.46, 0.94, 0.84, 0xFFFFC368);          // front leaf (two-tone)
            }
            default -> {
                box(g, x, y, w, h, 0.08, 0.18, 0.50, 0.32, 0xFFFFE9A8);          // raised tab
                box(g, x, y, w, h, 0.04, 0.28, 0.96, 0.88, 0xFF5A4708);          // hard outline base
                box(g, x, y, w, h, 0.08, 0.32, 0.92, 0.84, 0xFFF4C842);          // body
                box(g, x, y, w, h, 0.08, 0.32, 0.92, 0.42, 0xFFFFF3C4);          // highlight
            }
        }
    }

    private static void terminal(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                 final String os) {
        switch (os) {
            case "panes_xp" -> {
                box(g, x, y, w, h, 0.08, 0.10, 0.92, 0.24, 0xFF2B5BC0);          // blue title bar
                grad(g, x, y, w, h, 0.08, 0.24, 0.92, 0.90, 0xFF0B2030, 0xFF06120A); // glossy screen
                prompt(g, x, y, w, h, 0xFF8FE0A8);
            }
            case "panes_11" -> {
                box(g, x, y, w, h, 0.10, 0.12, 0.90, 0.88, 0xFF1C1E22);          // flat dark panel
                box(g, x, y, w, h, 0.10, 0.12, 0.90, 0.20, 0xFF2A2D33);          // thin header strip
                prompt(g, x, y, w, h, 0xFFE6E8EC);
            }
            default -> {
                box(g, x, y, w, h, 0.04, 0.08, 0.96, 0.92, 0xFF202024);          // hard bezel
                box(g, x, y, w, h, 0.12, 0.20, 0.88, 0.80, 0xFF06120A);          // screen
                prompt(g, x, y, w, h, 0xFF45E07A);
            }
        }
    }

    private static void prompt(final GuiGraphics g, final int x, final int y, final int w, final int h,
                               final int c) {
        box(g, x, y, w, h, 0.20, 0.38, 0.30, 0.48, c); // ">" upper
        box(g, x, y, w, h, 0.28, 0.46, 0.40, 0.56, c); // ">" tip
        box(g, x, y, w, h, 0.20, 0.54, 0.30, 0.64, c); // ">" lower
        box(g, x, y, w, h, 0.46, 0.60, 0.68, 0.68, c); // cursor
    }

    private static void editor(final GuiGraphics g, final int x, final int y, final int w, final int h,
                               final String os) {
        switch (os) {
            case "panes_xp" -> {
                grad(g, x, y, w, h, 0.18, 0.08, 0.82, 0.92, 0xFFFFFFFF, 0xFFDCE6F4); // glossy page
                frame(g, x, y, w, h, 0.18, 0.08, 0.82, 0.92, 0xFF5E80B5);
                lines(g, x, y, w, h, 0xFF9FB0CC);
                box(g, x, y, w, h, 0.58, 0.56, 0.88, 0.90, 0xFF2E5AB8);          // big blue pencil
            }
            case "panes_11" -> {
                box(g, x, y, w, h, 0.20, 0.08, 0.80, 0.92, 0xFFE9ECF2);          // flat page
                lines(g, x, y, w, h, 0xFF8A93A6);
                box(g, x, y, w, h, 0.20, 0.08, 0.30, 0.92, 0xFF4C84F0);          // left accent bar
            }
            default -> {
                box(g, x, y, w, h, 0.14, 0.06, 0.86, 0.94, 0xFF3A3A3A);          // hard outline
                box(g, x, y, w, h, 0.18, 0.10, 0.82, 0.90, 0xFFFFFFFF);          // page
                lines(g, x, y, w, h, 0xFF707888);
            }
        }
    }

    private static void lines(final GuiGraphics g, final int x, final int y, final int w, final int h,
                              final int c) {
        box(g, x, y, w, h, 0.26, 0.24, 0.74, 0.30, c);
        box(g, x, y, w, h, 0.26, 0.40, 0.74, 0.46, c);
        box(g, x, y, w, h, 0.26, 0.56, 0.56, 0.62, c);
    }

    private static void network(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                final String os) {
        switch (os) {
            case "panes_xp" -> {
                grad(g, x, y, w, h, 0.10, 0.10, 0.90, 0.90, 0xFF4FA0E8, 0xFF1A52A0); // glossy blue globe
                box(g, x, y, w, h, 0.10, 0.10, 0.90, 0.46, 0x44FFFFFF);          // gloss
                box(g, x, y, w, h, 0.18, 0.46, 0.82, 0.52, 0xFFBFE0FF);          // equator
                box(g, x, y, w, h, 0.46, 0.10, 0.52, 0.90, 0xFFBFE0FF);          // meridian
            }
            case "panes_11" -> {
                box(g, x, y, w, h, 0.18, 0.18, 0.34, 0.34, 0xFF5AA0F0);          // node
                box(g, x, y, w, h, 0.64, 0.22, 0.80, 0.38, 0xFF5AA0F0);          // node
                box(g, x, y, w, h, 0.42, 0.60, 0.58, 0.76, 0xFF5AA0F0);          // node
                box(g, x, y, w, h, 0.30, 0.26, 0.66, 0.30, 0xFF9FC4EE);          // flat link
                box(g, x, y, w, h, 0.30, 0.30, 0.36, 0.66, 0xFF9FC4EE);          // flat link
                box(g, x, y, w, h, 0.58, 0.34, 0.64, 0.66, 0xFF9FC4EE);          // flat link
            }
            default -> {
                box(g, x, y, w, h, 0.04, 0.08, 0.96, 0.92, 0xFF0E2D4E);          // hard outline panel
                box(g, x, y, w, h, 0.10, 0.14, 0.90, 0.86, 0xFF1E5AA0);
                box(g, x, y, w, h, 0.18, 0.20, 0.34, 0.36, 0xFFBFE0FF);          // node
                box(g, x, y, w, h, 0.64, 0.22, 0.80, 0.38, 0xFFBFE0FF);          // node
                box(g, x, y, w, h, 0.42, 0.58, 0.58, 0.74, 0xFFBFE0FF);          // node
            }
        }
    }

    private static void thisPc(final GuiGraphics g, final int x, final int y, final int w, final int h,
                               final String os) {
        switch (os) {
            case "panes_xp" -> {
                grad(g, x, y, w, h, 0.10, 0.12, 0.90, 0.64, 0xFFD8E4F2, 0xFF8FA8C8); // glossy monitor
                box(g, x, y, w, h, 0.16, 0.18, 0.84, 0.56, 0xFF4FA0E8);          // bright screen
                box(g, x, y, w, h, 0.16, 0.18, 0.84, 0.34, 0x55FFFFFF);          // gloss
                box(g, x, y, w, h, 0.30, 0.70, 0.70, 0.82, 0xFFB0C0D8);          // base
            }
            case "panes_11" -> {
                box(g, x, y, w, h, 0.12, 0.16, 0.88, 0.58, 0xFF2C3140);          // thin flat monitor
                box(g, x, y, w, h, 0.16, 0.20, 0.84, 0.54, 0xFF7FB8E0);          // screen
                box(g, x, y, w, h, 0.34, 0.62, 0.66, 0.70, 0xFF8A93A6);          // slim base
            }
            default -> {
                box(g, x, y, w, h, 0.06, 0.10, 0.94, 0.68, 0xFF202428);          // chunky CRT outline
                box(g, x, y, w, h, 0.12, 0.16, 0.74, 0.62, 0xFFC8CCD2);          // beige bezel
                box(g, x, y, w, h, 0.16, 0.20, 0.68, 0.56, 0xFF3A6EA5);          // screen
                box(g, x, y, w, h, 0.76, 0.20, 0.92, 0.62, 0xFFB0B4BA);          // side tower
            }
        }
    }

    /** The Network Management Studio: a database cylinder with a magnifier glass (the SSMS concept). */
    private static void nms(final GuiGraphics g, final int x, final int y, final int w, final int h,
                            final String os) {
        switch (os) {
            case "panes_xp" -> {
                grad(g, x, y, w, h, 0.14, 0.16, 0.66, 0.74, 0xFF6FB0F0, 0xFF1C5AB0); // glossy cylinder body
                box(g, x, y, w, h, 0.14, 0.16, 0.66, 0.26, 0xFFBFE0FF);          // top disc
                box(g, x, y, w, h, 0.14, 0.40, 0.66, 0.44, 0x66FFFFFF);          // band
                box(g, x, y, w, h, 0.14, 0.56, 0.66, 0.60, 0x66FFFFFF);          // band
                box(g, x, y, w, h, 0.50, 0.50, 0.84, 0.84, 0xFFE8F2FF);          // glass lens
                frame(g, x, y, w, h, 0.50, 0.50, 0.84, 0.84, 0xFF274F86);
                box(g, x, y, w, h, 0.78, 0.78, 0.94, 0.94, 0xFF274F86);          // glass handle
            }
            case "panes_11" -> {
                box(g, x, y, w, h, 0.16, 0.18, 0.64, 0.72, 0xFF4C84F0);          // flat cylinder
                box(g, x, y, w, h, 0.16, 0.18, 0.64, 0.28, 0xFF7FB0FA);          // top disc (two-tone)
                box(g, x, y, w, h, 0.16, 0.44, 0.64, 0.48, 0xFF2C5FB8);          // band
                box(g, x, y, w, h, 0.52, 0.52, 0.82, 0.82, 0xFFD7E6FF);          // glass lens
                frame(g, x, y, w, h, 0.52, 0.52, 0.82, 0.82, 0xFF3A6AC0);
                box(g, x, y, w, h, 0.76, 0.76, 0.92, 0.92, 0xFF3A6AC0);          // glass handle
            }
            default -> {
                box(g, x, y, w, h, 0.12, 0.14, 0.66, 0.76, 0xFF0E2D4E);          // hard outline
                box(g, x, y, w, h, 0.16, 0.16, 0.62, 0.74, 0xFF2E6AA8);          // cylinder body
                box(g, x, y, w, h, 0.16, 0.16, 0.62, 0.26, 0xFFBFE0FF);          // top disc
                box(g, x, y, w, h, 0.16, 0.40, 0.62, 0.44, 0xFFBFE0FF);          // band
                box(g, x, y, w, h, 0.16, 0.56, 0.62, 0.60, 0xFFBFE0FF);          // band
                box(g, x, y, w, h, 0.48, 0.48, 0.86, 0.86, 0xFF202428);          // glass outline
                box(g, x, y, w, h, 0.52, 0.52, 0.82, 0.82, 0xFFE8F2FF);          // lens
                box(g, x, y, w, h, 0.80, 0.80, 0.96, 0.96, 0xFF202428);          // handle
            }
        }
    }

    private static void generic(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                final String os) {
        switch (os) {
            case "panes_xp" -> {
                grad(g, x, y, w, h, 0.12, 0.12, 0.88, 0.88, 0xFFEDEFF4, 0xFFB8C0CE);
                frame(g, x, y, w, h, 0.12, 0.12, 0.88, 0.88, 0xFF7E8696);
            }
            case "panes_11" -> box(g, x, y, w, h, 0.16, 0.16, 0.84, 0.84, 0xFFC2C8D2);
            default -> {
                box(g, x, y, w, h, 0.10, 0.10, 0.90, 0.90, 0xFF3A3E46);
                box(g, x, y, w, h, 0.16, 0.16, 0.84, 0.84, 0xFFCFD3DA);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Proportional drawing helpers (fractions 0..1 of the icon box).
    // -------------------------------------------------------------------------

    private static void box(final GuiGraphics g, final int x, final int y, final int w, final int h,
                            final double a, final double b, final double c, final double d, final int color) {
        g.fill(x + (int) (a * w), y + (int) (b * h), x + (int) (c * w), y + (int) (d * h), color);
    }

    private static void grad(final GuiGraphics g, final int x, final int y, final int w, final int h,
                             final double a, final double b, final double c, final double d,
                             final int top, final int bottom) {
        g.fillGradient(x + (int) (a * w), y + (int) (b * h), x + (int) (c * w), y + (int) (d * h), top, bottom);
    }

    private static void frame(final GuiGraphics g, final int x, final int y, final int w, final int h,
                              final double a, final double b, final double c, final double d, final int color) {
        final int x0 = x + (int) (a * w);
        final int y0 = y + (int) (b * h);
        final int x1 = x + (int) (c * w);
        final int y1 = y + (int) (d * h);
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }
}
