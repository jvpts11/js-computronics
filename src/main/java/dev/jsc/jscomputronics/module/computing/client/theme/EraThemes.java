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
 * <p>The other five palettes are starting points (tunable in playtesting / a mockup pass): square corners only, no
 * rounded geometry — the eras differ by color first and by a restrained square overlay (scanlines, bevel, glow) only
 * where it reads as period-correct.
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

    /** Early-PC beige/blue chrome: light bevelled surfaces, classic system-blue accents, dark text on light. */
    public static final EraTheme LEGACY = new EraTheme(
            new EraPalette(
                    0xFF3A3A30, 0xFFC8C4B0, 0xFFB8B4A0, 0xFFD6D2C0, 0xFF6E6A58, 0xFFA8A494,
                    0xFFE4E0D0, 0xFF8A8676,
                    0xFF1A3C8C, 0xFF2E5AB8,
                    0xFF1E7A2E, 0xFFB8860B, 0xFFA01818,
                    0xFF1A1A14, 0xFF5A5648,
                    0xFF1A3C8C, 0xFFE8E4D4),
            new EraStyle(false, true, false, 0xFFFFFFF0, 0xFF6E6A58, 0, 0, SMALL));

    /** Sleeker dark: deeper graphite, a cool steel-blue accent and a violet secondary — STANDARD, one notch sharper. */
    public static final EraTheme ADVANCED = new EraTheme(
            new EraPalette(
                    0xFF04060A, 0xFF080B11, 0xFF0B0F16, 0xFF0E141C, 0xFF243042, 0xFF070B12,
                    0xFF070A10, 0xFF26354A,
                    0xFF4EA8FF, 0xFF8C6CFF,
                    0xFF5BE38A, 0xFFF2B544, 0xFFF06A6A,
                    0xFFE2E8F2, 0xFF8290A4,
                    0xFF14202E, 0xFF1B2A3C),
            EraStyle.flat(SMALL));

    /** Holographic near-future: deep teal/indigo panels, bright cyan/magenta accents, a faint square accent glow. */
    public static final EraTheme EXA = new EraTheme(
            new EraPalette(
                    0xFF030611, 0xFF060A1A, 0xFF081026, 0xFF0B1430, 0xFF1E60A0, 0xFF060C20,
                    0xFF05091C, 0xFF1E60A0,
                    0xFF22E0FF, 0xFFB04CFF,
                    0xFF3CF0C0, 0xFFFFC24A, 0xFFFF5E8A,
                    0xFFD6ECFF, 0xFF6E86B0,
                    0xFF0E2348, 0xFF14305C),
            new EraStyle(false, false, true, 0, 0, 0, 0x3322E0FF, SMALL));

    /** Exotic far-future glow: void-black with iridescent gold/violet/cyan accents and the strongest square glow. */
    public static final EraTheme SINGULARITY = new EraTheme(
            new EraPalette(
                    0xFF000000, 0xFF050308, 0xFF0A0612, 0xFF0D0818, 0xFF6A3CC0, 0xFF070410,
                    0xFF050208, 0xFF6A3CC0,
                    0xFFE0B84A, 0xFF9C5CFF,
                    0xFF50F0B0, 0xFFFFD25A, 0xFFFF4E7A,
                    0xFFF0E6FF, 0xFF8A6CC0,
                    0xFF170A2E, 0xFF22103E),
            new EraStyle(false, false, true, 0, 0, 0, 0x44E0B84A, SMALL));

    /**
     * The skin for a given hardware era. The enum is closed, so the switch is exhaustive.
     */
    public static EraTheme of(final HardwareEra era) {
        return switch (era) {
            case VINTAGE -> VINTAGE;
            case LEGACY -> LEGACY;
            case STANDARD -> STANDARD;
            case ADVANCED -> ADVANCED;
            case EXA -> EXA;
            case SINGULARITY -> SINGULARITY;
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
