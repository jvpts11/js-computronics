/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.theme;

import dev.jstech.core.client.gui.theme.EraThemes;
import dev.jstech.core.tier.HardwareEra;

/**
 * The look of the physical monitor frame a software screen is shown inside, chosen by the host computer's
 * hardware era. This is the bezel around the glass: an early machine shows a thick cream CRT shell with a dark
 * inner frame, a mid-era machine a slim grey LCD bezel, a modern machine a thin near-black flat bezel. Each frame
 * carries a model label and a power LED on a "chin" strip below the glass.
 *
 * <p>Pure data and geometry, with no Minecraft dependency, so the palette and the rectangle math stay
 * unit-testable; {@code MonitorFrame} does the actual drawing. Colors are ARGB ints and all geometry is square
 * (no rounded corners).
 *
 * @param bezelColor     the outer plastic shell color
 * @param bezelHighlight a 1px sheen line along the top edge ({@code 0} = off)
 * @param bezelShadow    a 1px shadow line along the bottom edge ({@code 0} = off)
 * @param bezelThickness px of shell around the glass on every side
 * @param innerColor     the dark inner frame between shell and glass ({@code 0} = none, for bezel-less LCD/flat)
 * @param innerThickness px of the inner frame ({@code 0} = none)
 * @param chinHeight     px of extra shell below the glass that holds the model label and the LED
 * @param model          the model label printed on the chin
 * @param labelColor     the model label color
 * @param ledColor       the power LED color (amber CRT, green LCD, blue flat)
 * @param ventColor      the color of the two vent slots on the chin ({@code 0} = no vents)
 */
public record MonitorFrameStyle(
        int bezelColor,
        int bezelHighlight,
        int bezelShadow,
        int bezelThickness,
        int innerColor,
        int innerThickness,
        int chinHeight,
        String model,
        int labelColor,
        int ledColor,
        int ventColor) {

    /** Total border between the glass and the outer shell edge (shell plus dark inner frame). */
    public int border() {
        return bezelThickness + innerThickness;
    }

    /**
     * The outer rectangle of the whole monitor for a glass (software screen) rectangle at {@code (sx, sy)} sized
     * {@code sw x sh}. The shell wraps the glass by {@link #border()} on every side and adds {@link #chinHeight}
     * below it for the label/LED strip.
     */
    public Geometry geometry(final int sx, final int sy, final int sw, final int sh) {
        final int b = border();
        final int x = sx - b;
        final int y = sy - b;
        final int w = sw + 2 * b;
        final int h = sh + 2 * b + chinHeight;
        final int chinY = sy + sh + b; // the chin strip sits directly below the bottom border
        return new Geometry(x, y, w, h, chinY);
    }

    /** Absolute pixel rectangle of the monitor body, plus the y where the chin (label/LED) strip starts. */
    public record Geometry(int x, int y, int w, int h, int chinY) {

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }
    }

    /**
     * The frame for a hardware era. Vintage is a cream CRT, Legacy a grey LCD, Standard a thin flat bezel. The
     * future eras (Advanced, Exa, Singularity) reuse the flat bezel until they get their own look, mirroring how
     * {@code EraThemes} resolves them to the Standard skin.
     */
    public static MonitorFrameStyle forEra(final HardwareEra era) {
        return switch (era) {
            case VINTAGE -> CRT;
            case LEGACY -> LCD;
            case STANDARD, ADVANCED, EXA, SINGULARITY -> FLAT;
        };
    }

    /** Cream CRT shell with a dark inner frame and an amber power LED. */
    public static final MonitorFrameStyle CRT = new MonitorFrameStyle(
            0xFFBDB295, 0xFFD8CFB5, 0xFF8A8068, 12,
            0xFF15140F, 4,
            18, "JSC-1400", 0xFF6E6650,
            0xFFFFB347, 0xFF8A8068);

    /** Slim grey LCD bezel with a green power LED. */
    public static final MonitorFrameStyle LCD = new MonitorFrameStyle(
            0xFFC4C6CC, 0xFFE0E1E5, 0xFF9A9CA2, 8,
            0, 0,
            16, "JSC LCD-700", 0xFF73757B,
            0xFF5EE06E, 0xFF9A9CA2);

    /** Thin near-black flat bezel with a blue power LED. */
    public static final MonitorFrameStyle FLAT = new MonitorFrameStyle(
            0xFF161618, 0xFF2A2A2E, 0xFF050506, 5,
            0, 0,
            12, "JSC", 0xFF5A5A60,
            0xFF7FB8FF, 0);
}
