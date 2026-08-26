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
 * a distinct look per OS (Panes 95 grey/teal, Panes XP blue Luna, Panes 11 light centered).
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

    public static DesktopTheme forOs(final ResourceLocation osId) {
        return switch (osId.getPath()) {
            case "panes_xp" -> XP;
            case "panes_11" -> WIN11;
            default -> WIN95;
        };
    }
}
