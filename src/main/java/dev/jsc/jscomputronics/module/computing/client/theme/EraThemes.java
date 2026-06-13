/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.theme;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import org.jetbrains.annotations.Nullable;

/**
 * The registry of concrete per-era GUI skins, plus the {@link HardwareEra} to {@link EraTheme} mapping a screen uses
 * to pick its skin. STANDARD is frozen: its palette is the exact set of values the original flat-dark theme shipped
 * with, and its style is flat, so a STANDARD-era (or no-board / unknown-era) screen renders pixel-for-pixel as before.
 *
 * <p>The Vintage and Legacy palettes are starting points (tunable in playtesting / a mockup pass): square corners
 * only, no rounded geometry — the eras differ by color first and by a restrained square overlay (scanlines, bevel)
 * only where it reads as period-correct. Eras beyond Standard ship no content yet, so they share the STANDARD skin.
 */
public final class EraThemes {

    private EraThemes() {
    }

    private static final float SMALL = 0.75f;

    /** Frozen copy of the original flat-dark constants — STANDARD must stay byte-identical to today. */
    public static final EraTheme STANDARD = new EraTheme(
            new EraPalette(
                    0xFF05070A, 0xFF0B0E13, 0xFF0E131A, 0xFF11161D, 0xFF1D2530, 0xFF0A0E14,
                    0xFF0A0D12, 0xFF1C2531,
                    0xFF39D6C4, 0xFF2AA7E0,
                    0xFF5FE07A, 0xFFF0B23A, 0xFFEF6A5A,
                    0xFFCDD6E2, 0xFF7D8A9C,
                    0xFF15212A, 0xFF1A2937),
            EraStyle.flat(SMALL));

    /** Green-phosphor CRT: near-black screen, phosphor green text/accents, amber cautions, a square scanline finish. */
    public static final EraTheme VINTAGE = new EraTheme(
            new EraPalette(
                    0xFF000000, 0xFF020A02, 0xFF031004, 0xFF051405, 0xFF1E5A1E, 0xFF030D03,
                    0xFF020A02, 0xFF1E5A1E,
                    0xFF33FF66, 0xFF66FF99,
                    0xFF33FF66, 0xFFFFB000, 0xFFFF6655,
                    0xFF66FF66, 0xFF2E8B2E,
                    0xFF0A2A0A, 0xFF103810),
            new EraStyle(true, false, false, 0, 0, 0x2200FF00, 0, SMALL));

    /**
     * Early-PC beige/blue chrome: bevelled surfaces, classic system-blue accents, dark text on warm-cream
     * backgrounds. The palette targets Windows 3.1 / early-90s PC BIOS aesthetics: raised buttons, sunken
     * display panels, and cream-white bevel highlights.
     */
    public static final EraTheme LEGACY = new EraTheme(
            new EraPalette(
                    0xFF808070, 0xFFC8C4B0, 0xFFB8B4A0, 0xFFD6D2C0, 0xFF6E6A58, 0xFF969280,
                    0xFFE4E0D0, 0xFF8A8676,
                    0xFF1A3C8C, 0xFF2E5AB8,
                    0xFF1E7A2E, 0xFFB8860B, 0xFFA01818,
                    0xFF1A1A14, 0xFF5A5648,
                    0xFF1A3C8C, 0xFFD4D0C0),
            new EraStyle(false, true, false, 0xFFFFFFF0, 0xFF6E6A58, 0, 0, SMALL));

    /**
     * The skin for a given hardware era. The enum is closed, so the switch is exhaustive. Only the Vintage, Legacy
     * and Standard eras ship content; the future eras (Advanced, Exa, Singularity) have no distinct skin and resolve
     * to STANDARD until their content exists.
     */
    public static EraTheme of(final HardwareEra era) {
        return switch (era) {
            case VINTAGE -> VINTAGE;
            case LEGACY -> LEGACY;
            case STANDARD, ADVANCED, EXA, SINGULARITY -> STANDARD;
        };
    }

    /**
     * The skin for a possibly-absent era. A screen with no valid build (no board installed) or no era available falls
     * back to STANDARD, so the GUI always has a defined skin and the default look never changes.
     */
    public static EraTheme ofNullable(@Nullable final HardwareEra era) {
        return era == null ? STANDARD : of(era);
    }
}
