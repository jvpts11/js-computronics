/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuiLayoutTest {

    @Test
    void overlaps_flagsTwoIntersectingBoxes() {
        final List<String> hits = new GuiLayout(200, 200)
                .box("a", 10, 10, 30, 30)
                .box("b", 25, 25, 30, 30)
                .overlaps();
        assertEquals(List.of("a x b"), hits);
    }

    @Test
    void overlaps_emptyForSeparatedBoxes() {
        final List<String> hits = new GuiLayout(200, 200)
                .box("a", 0, 0, 20, 20)
                .box("b", 30, 0, 20, 20)
                .overlaps();
        assertTrue(hits.isEmpty());
    }

    @Test
    void overlaps_treatsEdgeToEdgeAsNoOverlap() {
        // 'a' ends exactly where 'b' begins on the x axis: touching, not overlapping.
        final List<String> hits = new GuiLayout(200, 200)
                .box("a", 0, 0, 20, 20)
                .box("b", 20, 0, 20, 20)
                .overlaps();
        assertTrue(hits.isEmpty());
    }

    @Test
    void outOfBounds_flagsElementPastPanelHeight() {
        final List<String> hits = new GuiLayout(176, 166)
                .box("header", 8, 6, 50, 12)
                .box("footer", 8, 150, 20, 40) // 150 + 40 = 190 > 166
                .outOfBounds();
        assertEquals(List.of("footer"), hits);
    }

    @Test
    void outOfBounds_emptyWhenEverythingFits() {
        final List<String> hits = new GuiLayout(176, 166)
                .box("slot", 8, 8, 18, 18)
                .outOfBounds();
        assertTrue(hits.isEmpty());
    }

    @Test
    void text_widthScalesWithFontSize() {
        // 10 glyphs at scale 1.0 ~ 60 px; the same line at scale 2.0 ~ 120 px.
        final int small = new GuiLayout(400, 400).text("label", 0, 0, 10, 1.0f)
                .elements().get(0).width();
        final int big = new GuiLayout(400, 400).text("label", 0, 0, 10, 2.0f)
                .elements().get(0).width();
        assertEquals(60, small);
        assertEquals(120, big);
    }

    @Test
    void text_longLabelOverflowsNarrowPanel() {
        // A 40-glyph title at scale 1.0 is ~240 px, far wider than a 176 px panel.
        final List<String> hits = new GuiLayout(176, 166)
                .text("title", 8, 6, 40, 1.0f)
                .outOfBounds();
        assertEquals(List.of("title"), hits);
    }

    @Test
    void text_smallerScaleKeepsLongLabelInBounds() {
        // The same 40-glyph line at scale 0.5 is ~120 px and fits a 176 px panel.
        final List<String> hits = new GuiLayout(176, 166)
                .text("title", 8, 6, 40, 0.5f)
                .outOfBounds();
        assertTrue(hits.isEmpty());
    }

    @Test
    void isClean_trueForTidyLayout() {
        final boolean clean = new GuiLayout(176, 166)
                .box("slot", 8, 8, 18, 18)
                .text("label", 8, 30, 10, 1.0f)
                .isClean();
        assertTrue(clean);
    }

    @Test
    void isClean_falseWhenElementsOverlap() {
        final boolean clean = new GuiLayout(176, 166)
                .box("a", 8, 8, 40, 40)
                .box("b", 20, 20, 40, 40)
                .isClean();
        assertFalse(clean);
    }

    @Test
    void overlaps_ignoresTextDrawnOverASolid() {
        // A caption centered on a button must not be flagged: text-over-solid is intended.
        final List<String> hits = new GuiLayout(176, 166)
                .box("button", 66, 60, 96, 14)
                .text("caption", 90, 63, 10, 1.0f)
                .overlaps();
        assertTrue(hits.isEmpty());
    }

    @Test
    void overlaps_ignoresTextOverText() {
        final List<String> hits = new GuiLayout(176, 166)
                .text("label", 40, 26, 3, 1.0f)
                .text("value", 42, 26, 4, 1.0f)
                .overlaps();
        assertTrue(hits.isEmpty());
    }

    @Test
    void outOfBounds_stillCatchesTextEvenThoughOverlapsIgnoresIt() {
        // Text is exempt from overlap but never from the panel bounds.
        final List<String> hits = new GuiLayout(176, 166)
                .box("button", 66, 60, 96, 14)
                .text("runaway", 150, 160, 30, 1.0f)
                .outOfBounds();
        assertEquals(List.of("runaway"), hits);
    }

    @Test
    void playerInventory_addsThirtySixSlotsCleanly() {
        final GuiLayout l = new GuiLayout(176, 166).playerInventory(8, 84);
        assertEquals(36, l.elements().size());
        assertTrue(l.overlaps().isEmpty(), "inventory slots overlap: " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "inventory out of bounds: " + l.outOfBounds());
    }

    @Test
    void box_rejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new GuiLayout(100, 100).box("bad", 0, 0, -5, 10));
    }
}
