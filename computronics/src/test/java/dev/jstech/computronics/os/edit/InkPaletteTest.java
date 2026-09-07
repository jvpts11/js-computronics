/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.gui.ColorContrast;
import org.junit.jupiter.api.Test;

class InkPaletteTest {

    /**
     * The floor a piece of source has to clear against the paper it is on. Below this the colour is
     * there but the player cannot read it, which is the bug this test exists to stop.
     */
    private static final double READABLE = 3.5;

    /** A line number or a rule may be quieter than code, but it still has to be seen. */
    private static final double VISIBLE = 2.0;

    private static void assertReadable(final InkPalette palette, final String where) {
        for (final CodeRuns.Ink ink : CodeRuns.Ink.values()) {
            final double ratio = ColorContrast.ratio(palette.of(ink), palette.ground());
            assertTrue(ratio >= READABLE,
                    where + ": " + ink + " reads at " + String.format("%.2f", ratio) + " on its ground");
        }
    }

    @Test
    void of_isReadableOnTheLightGround() {
        assertReadable(InkPalette.LIGHT, "light");
    }

    @Test
    void of_isReadableOnTheDarkGround() {
        assertReadable(InkPalette.DARK, "dark");
    }

    @Test
    void gutterText_isVisibleOnTheGutterOfBothPalettes() {
        assertTrue(ColorContrast.ratio(InkPalette.LIGHT.gutterText(), InkPalette.LIGHT.gutter()) >= VISIBLE,
                "light gutter numbers are lost on the gutter");
        assertTrue(ColorContrast.ratio(InkPalette.DARK.gutterText(), InkPalette.DARK.gutter()) >= VISIBLE,
                "dark gutter numbers are lost on the gutter");
    }

    @Test
    void caret_standsOutAgainstTheGround() {
        assertTrue(ColorContrast.ratio(InkPalette.LIGHT.caret(), InkPalette.LIGHT.ground()) >= READABLE);
        assertTrue(ColorContrast.ratio(InkPalette.DARK.caret(), InkPalette.DARK.ground()) >= READABLE);
    }

    @Test
    void currentLine_isTellableFromTheGroundWithoutDrowningTheCodeOnIt() {
        for (final InkPalette palette : new InkPalette[] {InkPalette.LIGHT, InkPalette.DARK}) {
            assertTrue(palette.currentLine() != palette.ground(), "the current line is invisible");
            for (final CodeRuns.Ink ink : CodeRuns.Ink.values()) {
                assertTrue(ColorContrast.ratio(palette.of(ink), palette.currentLine()) >= READABLE,
                        ink + " is lost on the highlighted line");
            }
        }
    }

    @Test
    void forGround_picksThePaletteThatMatchesTheWindow() {
        assertEquals(InkPalette.DARK, InkPalette.forGround(true));
        assertEquals(InkPalette.LIGHT, InkPalette.forGround(false));
    }

    @Test
    void of_answersForEveryInkTheRunBuilderCanProduce() {
        for (final CodeRuns.Ink ink : CodeRuns.Ink.values()) {
            assertTrue((InkPalette.LIGHT.of(ink) >>> 24) == 0xFF, ink + " is not opaque on the light palette");
            assertTrue((InkPalette.DARK.of(ink) >>> 24) == 0xFF, ink + " is not opaque on the dark palette");
        }
    }
}
