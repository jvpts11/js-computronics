/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PatternEncoderLayoutTest {

    @Test
    void layout_hasNoOverlapsOrOverflow() {
        final GuiLayout l = PatternEncoderLayout.layout();
        assertTrue(l.isClean(), "Pattern Encoder layout is not clean: overlaps=" + l.overlaps()
                + " outOfBounds=" + l.outOfBounds());
    }

    @Test
    void layout_fitsTheScreenHeightBudget() {
        assertTrue(PatternEncoderLayout.HEIGHT <= 256,
                "encoder screen must fit the GUI height budget; got " + PatternEncoderLayout.HEIGHT);
    }

    @Test
    void buttons_sitBetweenTheBarAndTheInventoryLabel() {
        assertTrue(PatternEncoderLayout.BTN_Y >= PatternEncoderLayout.BAR_Y + PatternEncoderLayout.BAR_H + 4);
        assertTrue(PatternEncoderLayout.BTN_Y + PatternEncoderLayout.BTN_H <= PatternEncoderLayout.INV_LABEL_Y - 5);
    }

    @Test
    void infoLines_clearTheBar() {
        final int lastLineBottom = PatternEncoderLayout.INFO_Y + PatternEncoderLayout.LINE_H * 3 + 8;
        assertTrue(lastLineBottom <= PatternEncoderLayout.BAR_Y,
                "the status line must end above the bar; ends at " + lastLineBottom);
    }

    @Test
    void infoColumn_holdsTheLongestLineAtTheSmallFont() {
        // The longest fixed line the screen draws, at ~6 px per character before the small-font scale.
        final int longest = "Standard encoder: DVD, CD, USB".length();
        assertTrue(longest <= PatternEncoderLayout.INFO_CHARS);
        assertTrue(PatternEncoderLayout.INFO_CHARS * 6 * PatternEncoderLayout.INFO_SCALE <= PatternEncoderLayout.INFO_W);
    }

    @Test
    void inventory_isCentredAndInsideThePanel() {
        assertTrue(PatternEncoderLayout.INV_X >= 8);
        assertTrue(PatternEncoderLayout.INV_X + 9 * PatternEncoderLayout.SLOT <= PatternEncoderLayout.WIDTH - 8);
        assertTrue(PatternEncoderLayout.INV_Y + 58 + PatternEncoderLayout.SLOT <= PatternEncoderLayout.HEIGHT - 6);
    }
}
