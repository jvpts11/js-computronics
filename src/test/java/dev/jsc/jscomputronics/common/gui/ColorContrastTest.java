/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorContrastTest {

    @Test
    void ratio_blackOnWhite_isTheMaximum() {
        assertEquals(21.0, ColorContrast.ratio(0xFF000000, 0xFFFFFFFF), 0.1);
    }

    @Test
    void ratio_identicalColors_isOne() {
        assertEquals(1.0, ColorContrast.ratio(0xFF808080, 0xFF808080), 1e-9);
    }

    @Test
    void ratio_isSymmetric() {
        assertEquals(ColorContrast.ratio(0xFF1E1E1E, 0xFFFFFFFF),
                ColorContrast.ratio(0xFFFFFFFF, 0xFF1E1E1E), 1e-9);
    }

    @Test
    void ratio_ignoresAlpha() {
        assertEquals(ColorContrast.ratio(0xFF1E1E1E, 0xFFFFFFFF),
                ColorContrast.ratio(0x001E1E1E, 0x00FFFFFF), 1e-9);
    }

    @Test
    void ratio_lightGrayOnWhite_flagsTheOldUnreadableBug() {
        // The exact first bug: the dark theme's light-grey text (0xFFCDD6E2) drawn on the white editor.
        assertTrue(ColorContrast.ratio(0xFFCDD6E2, 0xFFFFFFFF) < 2.0,
                "light-grey on white must read as very low contrast");
    }

    @Test
    void luminance_isMonotonicFromBlackToWhite() {
        assertTrue(ColorContrast.luminance(0xFF000000) < ColorContrast.luminance(0xFF808080));
        assertTrue(ColorContrast.luminance(0xFF808080) < ColorContrast.luminance(0xFFFFFFFF));
    }
}
