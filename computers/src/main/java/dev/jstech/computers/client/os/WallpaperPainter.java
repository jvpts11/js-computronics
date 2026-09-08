/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.core.tier.HardwareEra;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Paints the desktop wallpaper for each Frames OS, evoking the real Windows background of that era
 * instead of a flat colour fill: Frames 95 a deep teal with a soft vertical shade, Frames XP a
 * Bliss-style sky over a rolling green hill, and Frames 11 a deep-blue gradient with a soft central
 * bloom. Drawn in desktop-local coordinates within the already-active scissor.
 */
final class WallpaperPainter {

    private WallpaperPainter() {
    }

    /** The wallpaper styles a player can pick, cycled by the desktop's Personalize action. */
    static final String[] STYLES = {"", "win95", "winxp", "win11", "breeze", "adwaita", "minty"};

    /**
     * Fills the desktop glass {@code (0,0)-(w,h)} with a wallpaper. The player's {@code choice}
     * overrides the OS default when set; an empty choice falls back to the OS's own look.
     *
     * @param g      the graphics context (pose already translated to the desktop origin)
     * @param w      desktop width in pixels
     * @param h      desktop height in pixels
     * @param osId   the installed OS id (the default look)
     * @param era    the host hardware era (reserved for future era-specific tints)
     * @param choice the player's chosen style id, or empty for the OS default
     */
    static void paint(final GuiGraphics g, final int w, final int h, final ResourceLocation osId,
                      final HardwareEra era, final String choice) {
        final String style = choice != null && !choice.isEmpty() ? choice : defaultStyle(osId);
        switch (style) {
            case "winxp" -> paintXp(g, w, h);
            case "win11" -> paintEleven(g, w, h);
            case "breeze" -> paintBreeze(g, w, h);
            case "adwaita" -> paintAdwaita(g, w, h);
            case "minty" -> paintMintY(g, w, h);
            default -> paintNineFive(g, w, h);
        }
    }

    /** A friendly label for a style id, for the Personalize menu. */
    static String styleLabel(final String style) {
        return switch (style) {
            case "win95" -> "Teal (95)";
            case "winxp" -> "Bliss (XP)";
            case "win11" -> "Bloom (11)";
            case "breeze" -> "Breeze (KDE)";
            case "adwaita" -> "Adwaita (GNOME)";
            case "minty" -> "Mint-Y (Cinnamon)";
            default -> "Default";
        };
    }

    /** The wallpaper a desktop environment ships with (the Frames editions' id doubles as their desktop id). */
    private static String defaultStyle(final ResourceLocation desktopId) {
        return switch (desktopId.getPath()) {
            case "frames_xp" -> "winxp";
            case "frames_11" -> "win11";
            case "kde_plasma" -> "breeze";
            case "gnome" -> "adwaita";
            case "cinnamon" -> "minty";
            default -> "win95";
        };
    }

    /** KDE Breeze: a deep blue field with a lighter glow high on the left. */
    private static void paintBreeze(final GuiGraphics g, final int w, final int h) {
        g.fillGradient(0, 0, w, h, 0xFF1D6FB8, 0xFF072747);
        g.fillGradient(0, 0, w * 2 / 3, h / 2, 0x552A8FE6, 0x00072747);
    }

    /** GNOME Adwaita: the blue-to-violet dusk gradient. */
    private static void paintAdwaita(final GuiGraphics g, final int w, final int h) {
        g.fillGradient(0, 0, w, h, 0xFF3B3F8F, 0xFF5A2D7A);
        g.fillGradient(0, h / 2, w, h, 0x00000000, 0x661D1F3A);
    }

    /** Cinnamon Mint-Y: a green-teal sweep brightening toward the bottom right. */
    private static void paintMintY(final GuiGraphics g, final int w, final int h) {
        g.fillGradient(0, 0, w, h, 0xFF1B5E4A, 0xFF2B8A6E);
        g.fillGradient(w / 2, h / 2, w, h, 0x0069B03B, 0x666FB98F);
    }

    /** Frames 95: the classic teal, lifted from flat by a subtle top-to-bottom shade. */
    private static void paintNineFive(final GuiGraphics g, final int w, final int h) {
        g.fillGradient(0, 0, w, h, 0xFF1F8A8A, 0xFF135E5E);
    }

    /** Frames XP: a Bliss-style sky fading to the horizon over a rolling green hill. */
    private static void paintXp(final GuiGraphics g, final int w, final int h) {
        final int horizon = (int) (h * 0.60);
        // Sky: deep blue overhead fading to a pale band at the horizon, with soft clouds drifting in it.
        g.fillGradient(0, 0, w, horizon, 0xFF1F5FC0, 0xFFC4DFF6);
        cloud(g, (int) (w * 0.22), (int) (h * 0.21), (int) (w * 0.19), (int) (h * 0.065));
        cloud(g, (int) (w * 0.33), (int) (h * 0.15), (int) (w * 0.11), (int) (h * 0.045));
        cloud(g, (int) (w * 0.75), (int) (h * 0.25), (int) (w * 0.21), (int) (h * 0.065));
        cloud(g, (int) (w * 0.87), (int) (h * 0.18), (int) (w * 0.12), (int) (h * 0.045));
        // Grass: bright near the horizon down to a deeper green at the bottom.
        g.fillGradient(0, horizon, w, h, 0xFF74AE3E, 0xFF2F5A18);
        /*
         * The hill the whole picture is named after: a broad crest left of centre, its sunlit rim along the
         * top and a second, nearer rise on the right, so the field reads as land instead of a flat band.
         */
        final int crest = Math.max(4, (int) (h * 0.15));
        for (int x = 0; x < w; x += 2) {
            final double t = (double) x / Math.max(1, w - 1);
            final int rise = (int) (crest * Math.sin(Math.PI * Math.pow(t, 0.75)));
            if (rise <= 0) {
                continue;
            }
            final int top = horizon - rise;
            final int x2 = Math.min(x + 2, w);
            g.fill(x, top, x2, horizon, 0xFF63A032);
            g.fill(x, top, x2, top + 1, 0xFF9BD164);
        }
        final int near = Math.max(3, (int) (h * 0.09));
        for (int x = w / 2; x < w; x += 2) {
            final double t = (double) (x - w / 2) / Math.max(1, w / 2 - 1);
            final int rise = (int) (near * Math.sin(Math.PI * t));
            if (rise <= 0) {
                continue;
            }
            final int top = horizon - rise;
            final int x2 = Math.min(x + 2, w);
            /*
             * No lit rim on this one: it is the nearer rise, and its edge reads as a silhouette against the
             * hill behind it. A bright line there would look like a scratch across the field.
             */
            g.fill(x, top, x2, horizon, 0xFF4E8B26);
        }
        // A shade settling into the field just under the horizon, the way the far grass falls into shadow.
        g.fillGradient(0, horizon, w, horizon + Math.max(2, h / 40), 0x00000000, 0x2A0E2A08);
    }

    /**
     * One soft cloud. Each row is three nested spans of the same thin white, so the alpha piles up towards
     * the middle both across and down the shape: it fades out at every edge instead of showing the rings a
     * few stacked ellipses would leave.
     */
    private static void cloud(final GuiGraphics g, final int cx, final int cy, final int rx, final int ry) {
        if (rx <= 0 || ry <= 0) {
            return;
        }
        for (int dy = -ry; dy <= ry; dy++) {
            final double k = 1.0 - (double) (dy * dy) / (double) (ry * ry);
            if (k <= 0.0) {
                continue;
            }
            final int half = (int) (rx * Math.sqrt(k));
            if (half <= 0) {
                continue;
            }
            final int row = cy + dy;
            g.fill(cx - half, row, cx + half, row + 1, 0x14FFFFFF);
            final int mid = half * 72 / 100;
            g.fill(cx - mid, row, cx + mid, row + 1, 0x14FFFFFF);
            final int core = half * 42 / 100;
            g.fill(cx - core, row, cx + core, row + 1, 0x18FFFFFF);
        }
    }

    /** Frames 11: a deep-blue gradient with a soft central bloom. */
    private static void paintEleven(final GuiGraphics g, final int w, final int h) {
        g.fillGradient(0, 0, w, h, 0xFF1E3E74, 0xFF0B1530);
        // Soft central bloom: a few translucent light-blue bands, brightest in the middle.
        final int cx = w / 2;
        final int cy = (int) (h * 0.42);
        for (int i = 3; i >= 0; i--) {
            final int rx = 36 + i * 26;
            final int ry = 22 + i * 16;
            g.fill(cx - rx, cy - ry, cx + rx, cy + ry, 0x165A8AD8);
        }
    }
}
