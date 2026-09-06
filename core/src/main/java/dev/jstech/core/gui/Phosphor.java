/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.gui;

/**
 * A monochrome monitor has one colour.
 *
 * <p>The tube of the Vintage era is a green-phosphor CRT: every pixel it can light is the same green,
 * brighter or dimmer. Screens that run on such a monitor were written with the full palette anyway —
 * white headings, cyan system lines, red errors, amber warnings — which no monitor of that decade could
 * have shown. This maps any colour to what that tube would actually display: the phosphor, lit in
 * proportion to how bright the original colour reads. Errors stay legible because red is dim and
 * headings still stand out because white is full brightness, so the meaning survives the filter.
 *
 * <p>Pure maths, no rendering: the screens call it, and a unit test can check it.
 */
public final class Phosphor {

    /** The green a P1-type tube glows: the Vintage skin's own text colour. */
    public static final int GREEN = 0xFF33FF66;

    private Phosphor() {
    }

    /** {@code argb} as the green tube would show it, keeping its alpha and its relative brightness. */
    public static int green(final int argb) {
        return tint(argb, GREEN);
    }

    /** As {@link #green}, against an arbitrary phosphor colour. */
    public static int tint(final int argb, final int phosphor) {
        final int alpha = argb >>> 24;
        final float lit = luminance(argb);
        return (alpha << 24)
                | (lit((phosphor >> 16) & 0xFF, lit) << 16)
                | (lit((phosphor >> 8) & 0xFF, lit) << 8)
                | lit(phosphor & 0xFF, lit);
    }

    /** How brightly a colour reads, 0 (black) to 1 (white), by the usual perceptual weights. */
    public static float luminance(final int argb) {
        final float r = ((argb >> 16) & 0xFF) / 255.0F;
        final float g = ((argb >> 8) & 0xFF) / 255.0F;
        final float b = (argb & 0xFF) / 255.0F;
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    private static int lit(final int channel, final float amount) {
        return Math.max(0, Math.min(255, Math.round(channel * amount)));
    }
}
