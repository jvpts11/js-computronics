/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.theme;

import dev.jstech.computronics.client.os.OsSkin;
import dev.jstech.core.tier.HardwareEra;
import org.jetbrains.annotations.Nullable;

/**
 * GUI skins for the Network Management Studio, kept apart from the general {@link EraThemes} because a program has
 * its own visual identity rather than the host computer's OS skin. The Studio is a deliberate clone of classic SQL
 * Server Management Studio, so its STANDARD-era skin is a LIGHT theme (white panels, system-blue status bar) — unlike
 * every other computing screen, which stays on the dark OS skin.
 *
 * <p>The Studio still varies by hardware era: {@link #of} resolves an era to its Studio skin. Only the modern
 * ({@code STANDARD}) skin ships now; earlier eras have no period-correct Studio skin yet and fall back to it, the same
 * way {@link EraThemes} shares STANDARD for eras without content. Their retro skins land with the eras build.
 */
public final class NmsThemes {

    private NmsThemes() {
    }

    /**
     * The classic SSMS light skin over {@link NmsPalette#SSMS_LIGHT}: a light-grey window, white panels, grey
     * separators, near-black text, system-blue accents, the green/red status colors, and the warm-yellow table
     * icons ({@code amber}). The style is flat (no bevels/scanlines) for the clean modern look.
     */
    public static final EraTheme SSMS_LIGHT = new EraTheme(NmsPalette.SSMS_LIGHT, EraStyle.flat(0.75f));

    /**
     * The Studio skin for a given hardware era. Today every era uses the modern {@link #SSMS_LIGHT} skin; the per-era
     * retro variants (a green-CRT Studio, a beige Win3.1 Studio) arrive with the eras build, when this switch grows.
     */
    public static EraTheme of(@Nullable final HardwareEra era) {
        return SSMS_LIGHT;
    }

    /**
     * The Studio skin for the installed OS: the SSMS layout kept, but the palette re-derived from the OS skin so
     * the Studio follows Frames 95/XP/11 like every other program. The semantic colours (green/amber/red status,
     * the editor track) stay fixed; structure and the flat style are unchanged.
     */
    public static EraTheme forOs(final OsSkin skin) {
        final EraPalette base = NmsPalette.SSMS_LIGHT;
        final EraPalette p = new EraPalette(
                skin.windowBg(), skin.fieldBg(), skin.panelBg(), skin.panelBg(), skin.edge(), base.track(),
                skin.fieldBg(), skin.edge(),
                skin.accent(), skin.accent(),
                base.green(), base.amber(), base.red(),
                skin.text(), skin.dim(),
                skin.accent(), 0xFFFFFFFF, skin.listHover());
        return new EraTheme(p, EraStyle.flat(0.75f));
    }
}
