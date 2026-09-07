/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhosphorTest {

    private static int red(final int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int greenOf(final int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(final int argb) {
        return argb & 0xFF;
    }

    @Test
    void green_turnsEveryColourIntoTheOnePhosphor() {
        /*
         * Whatever goes in, what comes out is the tube's own green at some brightness: the ratio between
         * the channels is the phosphor's, never the source colour's.
         */
        for (final int colour : new int[] {0xFFFFFFFF, 0xFFEF6A5A, 0xFF39D6C4, 0xFFF0B23A, 0xFF2AA7E0}) {
            final int lit = Phosphor.green(colour);
            assertTrue(greenOf(lit) >= red(lit) && greenOf(lit) >= blue(lit),
                    "green must dominate for " + Integer.toHexString(colour));
            assertTrue(red(lit) <= blue(lit), "the phosphor leans green-cyan, not green-red");
        }
    }

    @Test
    void green_keepsBrightnessOrderSoMeaningSurvives() {
        /*
         * An error is dim and a heading is bright on a real monochrome monitor; that ordering is what lets
         * a player still read the screen once the colour is gone.
         */
        assertTrue(Phosphor.luminance(Phosphor.green(0xFFFFFFFF)) > 0.0F, "white lights the tube fully");
        assertTrue(Phosphor.luminance(Phosphor.green(0xFFEF6A5A)) > 0.0F, "a red error still lights the tube");
        assertTrue(Phosphor.luminance(Phosphor.green(0xFFFFFFFF))
                > Phosphor.luminance(Phosphor.green(0xFF7D8A9C)), "white is brighter than a dim grey");
        assertTrue(Phosphor.luminance(Phosphor.green(0xFF7D8A9C))
                > Phosphor.luminance(Phosphor.green(0xFF303030)), "a dim grey is brighter than near-black");
    }

    @Test
    void green_leavesBlackBlackAndKeepsAlpha() {
        assertEquals(0xFF000000, Phosphor.green(0xFF000000), "an unlit pixel stays unlit");
        assertEquals(0x80, Phosphor.green(0x80FFFFFF) >>> 24, "alpha passes through untouched");
    }

    @Test
    void green_ofWhiteIsTheTubesOwnColour() {
        assertEquals(Phosphor.GREEN, Phosphor.green(0xFFFFFFFF),
                "full brightness is exactly the phosphor the Vintage skin uses");
    }

    @Test
    void luminance_weightsGreenMostAndBlueLeast() {
        assertTrue(Phosphor.luminance(0xFF00FF00) > Phosphor.luminance(0xFFFF0000));
        assertTrue(Phosphor.luminance(0xFFFF0000) > Phosphor.luminance(0xFF0000FF));
        assertEquals(0.0F, Phosphor.luminance(0xFF000000));
        assertEquals(1.0F, Phosphor.luminance(0xFFFFFFFF), 0.0001F);
    }
}
