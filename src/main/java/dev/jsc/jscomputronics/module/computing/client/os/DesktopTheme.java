/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import net.minecraft.resources.ResourceLocation;

/**
 * The visual chrome of a desktop OS: wallpaper, taskbar, start button, window title bars, and text
 * colors. Each graphical OS picks its theme by id, so the shared {@link DesktopScreen} engine renders
 * a distinct look per OS (Frames 95 grey/teal, Frames XP blue Luna, Frames 11 light centered).
 *
 * <p>All colors are ARGB. Values are tunable to match the in-game look.
 */
public record DesktopTheme(
        int wallpaper, int taskbar, int taskbarEdge, int startButton, int startText,
        int taskButton, int iconTile, int iconText, int menuBg, int menuText,
        int titleActive, int titleText, int windowBg, int windowBorder,
        String startLabel, boolean textShadow) {

    private static final DesktopTheme WIN95 = new DesktopTheme(
            0xFF1C7C7C, 0xFFC0C0C0, 0xFFFFFFFF, 0xFFC0C0C0, 0xFF000000,
            0xFFC0C0C0, 0xFF9090A8, 0xFFFFFFFF, 0xFFC0C0C0, 0xFF000000,
            0xFF000080, 0xFFFFFFFF, 0xFFC0C0C0, 0xFF808080,
            "Start", true);

    private static final DesktopTheme XP = new DesktopTheme(
            0xFF5B8AC4, 0xFF295FBE, 0xFF6E9BE0, 0xFF3FA13F, 0xFFFFFFFF,
            0xFF4F7FCB, 0xFF9DBCE8, 0xFFFFFFFF, 0xFFECECF6, 0xFF101030,
            0xFF295FBE, 0xFFFFFFFF, 0xFFECECF6, 0xFF1A3A78,
            "Start", true);

    private static final DesktopTheme WIN11 = new DesktopTheme(
            0xFF1E2A47, 0xFFF1F2F6, 0xFFD8DAE2, 0xFFF1F2F6, 0xFF202434,
            0xFFE3E5EE, 0xFF2A3656, 0xFFFFFFFF, 0xFFFAFAFE, 0xFF202434,
            0xFF2A3656, 0xFFFFFFFF, 0xFFFAFAFE, 0xFFC0C4D2,
            "Start", false);

    // KDE Plasma — Breeze: deep blue wallpaper, dark panel, sky-blue accents, light window chrome.
    private static final DesktopTheme KDE = new DesktopTheme(
            0xFF0C3D73, 0xFF1B1E24, 0xFF2A2F38, 0xFF3DAEE9, 0xFFEFF0F1,
            0xFF2A2F38, 0xFF1F5A8A, 0xFFFFFFFF, 0xFF31363B, 0xFFEFF0F1,
            0xFF3DAEE9, 0xFFEFF0F1, 0xFFEFF0F1, 0xFFB9BFC8,
            "K", false);

    // GNOME — Adwaita: blue-violet wallpaper, black top bar, GNOME blue accents.
    private static final DesktopTheme GNOME = new DesktopTheme(
            0xFF3B3F8F, 0xFF0F0F12, 0xFF1F1F24, 0xFF0F0F12, 0xFFFFFFFF,
            0xFF2A2A30, 0xFF2F3380, 0xFFFFFFFF, 0xFFF6F5F4, 0xFF2E3436,
            0xFF3584E4, 0xFF2E3436, 0xFFF6F5F4, 0xFFC0BFBC,
            "Activities", false);

    // Cinnamon — Mint-Y: green-teal wallpaper, dark grey panel, Mint green accents.
    private static final DesktopTheme CINNAMON = new DesktopTheme(
            0xFF2B8A6E, 0xFF2B2B2B, 0xFF3A3A3A, 0xFF69B03B, 0xFFE3E3E3,
            0xFF3A3A3A, 0xFF1F6650, 0xFFFFFFFF, 0xFF2F2F2F, 0xFFE8E8E8,
            0xFF69B03B, 0xFF2B2B2B, 0xFFF7F7F7, 0xFFB0B0B0,
            "Menu", false);

    /** The theme for an OS id; the Frames editions bundle their own desktop, so the id doubles as the desktop id. */
    public static DesktopTheme forOs(final ResourceLocation osId) {
        return forDesktop(osId);
    }

    /** The theme for a desktop environment id; Frames 95 is the fallback. */
    public static DesktopTheme forDesktop(final ResourceLocation desktopId) {
        return switch (desktopId.getPath()) {
            case "frames_xp" -> XP;
            case "frames_11" -> WIN11;
            case "kde_plasma" -> KDE;
            case "gnome" -> GNOME;
            case "cinnamon" -> CINNAMON;
            default -> WIN95;
        };
    }
}
