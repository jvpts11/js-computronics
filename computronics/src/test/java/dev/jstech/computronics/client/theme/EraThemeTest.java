/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.theme;

import dev.jstech.core.tier.HardwareEra;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pure-logic checks for the era-to-theme selection and the frozen STANDARD palette. No Minecraft types are touched:
 * the draw helpers (which take GuiGraphics) are never linked here, only the palette/selection are exercised.
 */
class EraThemeTest {

    // The STANDARD palette is frozen — these are the exact values the original flat-dark theme shipped with.
    private static final int STD_OUTER = 0xFF05070A;
    private static final int STD_SCREEN = 0xFF0B0E13;
    private static final int STD_PANEL = 0xFF11161D;
    private static final int STD_LINE = 0xFF1D2530;
    private static final int STD_ACCENT = 0xFF39D6C4;
    private static final int STD_GREEN = 0xFF5FE07A;
    private static final int STD_AMBER = 0xFFF0B23A;
    private static final int STD_RED = 0xFFEF6A5A;
    private static final int STD_TEXT = 0xFFCDD6E2;
    private static final int STD_DIM = 0xFF7D8A9C;

    @Test
    void of_standard_returnsTheStandardSingleton() {
        assertSame(EraThemes.STANDARD, EraThemes.of(HardwareEra.STANDARD));
    }

    @Test
    void of_standard_keyColorsEqualTheFrozenStandardValues() {
        final EraTheme t = EraThemes.of(HardwareEra.STANDARD);
        assertEquals(STD_OUTER, t.outer());
        assertEquals(STD_SCREEN, t.screen());
        assertEquals(STD_PANEL, t.panel());
        assertEquals(STD_LINE, t.line());
        assertEquals(STD_ACCENT, t.accent());
        assertEquals(STD_GREEN, t.green());
        assertEquals(STD_AMBER, t.amber());
        assertEquals(STD_RED, t.red());
        assertEquals(STD_TEXT, t.text());
        assertEquals(STD_DIM, t.dim());
    }

    @Test
    void of_standard_smallFontScaleIsThreeQuarters() {
        assertEquals(0.75f, EraThemes.of(HardwareEra.STANDARD).small());
    }

    @Test
    void of_standard_hasNoOverlays() {
        final EraStyle s = EraThemes.of(HardwareEra.STANDARD).style();
        assertEquals(false, s.scanlines());
        assertEquals(false, s.doubleBevel());
        assertEquals(false, s.glowAccent());
        assertEquals(0, s.scanlineColor());
        assertEquals(0, s.bevelLight());
        assertEquals(0, s.bevelDark());
        assertEquals(0, s.glowColor());
    }

    @Test
    void of_vintage_differsFromStandard() {
        final EraTheme vintage = EraThemes.of(HardwareEra.VINTAGE);
        assertNotEquals(EraThemes.STANDARD, vintage);
        // A green-phosphor CRT inverts the accent/text away from the cyan flat-dark values.
        assertNotEquals(STD_ACCENT, vintage.accent());
        assertNotEquals(STD_TEXT, vintage.text());
        assertNotEquals(STD_SCREEN, vintage.screen());
    }

    @Test
    void of_vintage_enablesScanlinesWithAColor() {
        final EraStyle s = EraThemes.of(HardwareEra.VINTAGE).style();
        assertEquals(true, s.scanlines());
        assertNotEquals(0, s.scanlineColor());
    }

    @Test
    void of_legacy_usesDarkTextOnLight() {
        // Legacy is the contrast-inverted era: near-black text over a light beige screen.
        final EraTheme legacy = EraThemes.of(HardwareEra.LEGACY);
        assertEquals(true, legacy.style().doubleBevel());
        assertNotEquals(STD_TEXT, legacy.text());
    }

    @Test
    void everyEra_tabLabelOnDiffersFromTabOn() {
        // tabLabelOn must contrast with tabOn: if they were the same, labels drawn on a selected tab
        // would be invisible. Verified as a structural invariant: tabLabelOn != tabOn for every era.
        for (final HardwareEra era : HardwareEra.values()) {
            final EraTheme t = EraThemes.of(era);
            assertNotEquals(t.tabOn(), t.tabLabelOn(),
                    "Era " + era + ": tabLabelOn must differ from tabOn");
        }
    }

    @Test
    void everyEra_resolvesToANonNullTheme() {
        for (final HardwareEra era : HardwareEra.values()) {
            assertNotEquals(null, EraThemes.of(era));
        }
    }

    @Test
    void of_futureEras_resolveToStandard() {
        // The eras beyond Standard ship no content yet, so they have no distinct skin and fall back to STANDARD.
        assertSame(EraThemes.STANDARD, EraThemes.of(HardwareEra.ADVANCED));
        assertSame(EraThemes.STANDARD, EraThemes.of(HardwareEra.EXA));
        assertSame(EraThemes.STANDARD, EraThemes.of(HardwareEra.SINGULARITY));
    }

    @Test
    void ofNullable_null_fallsBackToStandard() {
        assertSame(EraThemes.STANDARD, EraThemes.ofNullable(null));
    }

    @Test
    void ofNullable_presentEra_matchesOf() {
        for (final HardwareEra era : HardwareEra.values()) {
            assertSame(EraThemes.of(era), EraThemes.ofNullable(era));
        }
    }
}
