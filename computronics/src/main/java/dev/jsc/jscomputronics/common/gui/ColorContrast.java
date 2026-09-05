/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.gui;

/**
 * Pure color math for checking that GUI text stays readable on its background. The Studio shipped a couple
 * of unreadable-text bugs (near-invisible light-grey text on white), so a screen's palette can be unit-tested
 * for contrast here instead of being caught only in-game. No Minecraft types — plain ARGB ints.
 *
 * <p>The ratio is the standard WCAG contrast ratio (1.0 for identical colors, up to 21.0 for black on white):
 * {@code (Lhi + 0.05) / (Llo + 0.05)} over the two colors' relative luminances. As a rough guide, body text
 * wants &ge; 4.5, large or secondary text &ge; 3.0.
 */
public final class ColorContrast {

    private ColorContrast() {
    }

    /** The WCAG contrast ratio (1.0..21.0) between two opaque ARGB colors; alpha is ignored. */
    public static double ratio(final int argbA, final int argbB) {
        final double la = luminance(argbA);
        final double lb = luminance(argbB);
        final double hi = Math.max(la, lb);
        final double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** Relative luminance (0.0..1.0) of an ARGB color, per WCAG (sRGB channels linearized). */
    public static double luminance(final int argb) {
        final double r = linear(((argb >> 16) & 0xFF) / 255.0);
        final double g = linear(((argb >> 8) & 0xFF) / 255.0);
        final double b = linear((argb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linear(final double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
