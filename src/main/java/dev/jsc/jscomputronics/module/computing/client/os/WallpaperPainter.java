/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
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
        final int horizon = (int) (h * 0.62);
        // Sky: deeper blue up top, fading to a pale band near the horizon.
        g.fillGradient(0, 0, w, horizon, 0xFF3E72B4, 0xFFBFD8F2);
        // Grass: bright near the horizon down to a deeper green at the bottom.
        g.fillGradient(0, horizon, w, h, 0xFF6FA63C, 0xFF34611C);
        // A rolling hill rising above the flat horizon (a soft sine bump) in the grass colour,
        // with a thin sunlit rim along its crest.
        for (int x = 0; x < w; x += 2) {
            final int rise = (int) (10.0 * Math.sin(Math.PI * x / w));
            final int top = horizon - rise;
            final int x2 = Math.min(x + 2, w);
            g.fill(x, top, x2, horizon, 0xFF5E9433);
            g.fill(x, top, x2, top + 1, 0xFF8FC65A);
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
