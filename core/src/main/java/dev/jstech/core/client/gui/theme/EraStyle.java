/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.theme;

/**
 * Style flags layered on top of a {@link EraPalette}. These drive the optional, era-specific surface overlays;
 * all of them are sharp/square (no rounded corners ever). Every overlay is a no-op when its flag is off or its
 * color is {@code 0}, which is exactly what lets the STANDARD style reproduce today's output byte-for-byte.
 *
 * @param scanlines      draw a faint horizontal scanline overlay over the window background (CRT look)
 * @param doubleBevel    draw a 1px raised/inset bevel on panels, buttons and slots instead of a flat 1px line
 * @param glowAccent     lay a faint additive halo around accent-colored fills drawn through the helpers
 * @param bevelLight     ARGB of the bevel highlight edge ({@code 0} = off)
 * @param bevelDark      ARGB of the bevel shadow edge ({@code 0} = off)
 * @param scanlineColor  ARGB (low alpha) of the scanline lines ({@code 0} = off)
 * @param glowColor      ARGB (low alpha) of the accent glow halo ({@code 0} = off)
 * @param fontScaleSmall the scale factor for the small-text helpers (the former {@code JsTechTheme.SMALL})
 */
public record EraStyle(
        boolean scanlines,
        boolean doubleBevel,
        boolean glowAccent,
        int bevelLight,
        int bevelDark,
        int scanlineColor,
        int glowColor,
        float fontScaleSmall) {

    /** A flat style with every overlay disabled, the STANDARD baseline. {@code small} is the only live value. */
    public static EraStyle flat(final float fontScaleSmall) {
        return new EraStyle(false, false, false, 0, 0, 0, 0, fontScaleSmall);
    }
}
