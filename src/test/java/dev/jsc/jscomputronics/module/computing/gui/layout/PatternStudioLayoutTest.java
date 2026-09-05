/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jsc.jscomputronics.common.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

class PatternStudioLayoutTest {

    /**
     * The content a program gets on a standard monitor without maximizing: the glass is at most 256 tall, the
     * taskbar takes 24, the desktop keeps 16 around a new window, and the window's title and margins take 22.
     */
    private static final int STANDARD_WINDOW_CONTENT_H = 256 - 24 - 16 - 22;
    /** The content of a window maximized on that monitor. */
    private static final int MAXIMIZED_CONTENT_H = 256 - 24 - 22;
    private static final int CONTENT_W = 330 - 8;

    @Test
    void bandVisible_fitsAStandardMonitorWindow() {
        assertTrue(PatternStudioLayout.bandVisible(STANDARD_WINDOW_CONTENT_H),
                "the band needs " + PatternStudioLayout.minContentHeight() + " but a standard window gives "
                        + STANDARD_WINDOW_CONTENT_H);
    }

    @Test
    void bandLabelVisible_onlyWhenTheWindowHasRoomToSpare() {
        assertFalse(PatternStudioLayout.bandLabelVisible(STANDARD_WINDOW_CONTENT_H));
        assertTrue(PatternStudioLayout.bandLabelVisible(MAXIMIZED_CONTENT_H));
    }

    @Test
    void bandTop_keepsTheBandAboveTheBarAndTheEditorAboveTheBand() {
        final int top = PatternStudioLayout.bandTop(STANDARD_WINDOW_CONTENT_H);
        assertEquals(STANDARD_WINDOW_CONTENT_H - PatternStudioLayout.BAR_H, top + PatternStudioLayout.BAND_H);
        assertEquals(top, PatternStudioLayout.editorBottom(STANDARD_WINDOW_CONTENT_H));
        final int labelledTop = PatternStudioLayout.bandTop(MAXIMIZED_CONTENT_H);
        assertEquals(labelledTop - PatternStudioLayout.BAND_LABEL_H,
                PatternStudioLayout.editorBottom(MAXIMIZED_CONTENT_H));
    }

    @Test
    void editorBottom_runsDownToTheBarWhenTheBandFolds() {
        final int tight = PatternStudioLayout.minContentHeight() - 1;
        assertFalse(PatternStudioLayout.bandVisible(tight));
        assertEquals(tight - PatternStudioLayout.BAR_H, PatternStudioLayout.editorBottom(tight));
        assertTrue(PatternStudioLayout.editorBottom(tight) - PatternStudioLayout.TAB_H
                >= PatternStudioLayout.EDITOR_MIN_H);
    }

    @Test
    void layout_isCleanOnTheStandardWindowAndMaximized() {
        for (final int h : new int[] {STANDARD_WINDOW_CONTENT_H, MAXIMIZED_CONTENT_H,
                PatternStudioLayout.minContentHeight() - 1}) {
            final GuiLayout layout = PatternStudioLayout.layout(CONTENT_W, h);
            assertTrue(layout.isClean(),
                    "h=" + h + " overlaps=" + layout.overlaps() + " outOfBounds=" + layout.outOfBounds());
        }
    }
}
