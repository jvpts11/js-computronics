/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollStateTest {

    @Test
    void of_startsAtZeroOffset() {
        ScrollState s = ScrollState.of(100, 10);
        assertEquals(0, s.offset());
    }

    @Test
    void maxOffset_whenMoreItemsThanVisible() {
        ScrollState s = ScrollState.of(100, 10);
        assertEquals(90, s.maxOffset());
    }

    @Test
    void maxOffset_zeroWhenFewerItemsThanVisible() {
        ScrollState s = ScrollState.of(5, 10);
        assertEquals(0, s.maxOffset());
    }

    @Test
    void scrolledBy_clampsAtZero() {
        ScrollState s = ScrollState.of(100, 10).scrolledBy(-5);
        assertEquals(0, s.offset());
    }

    @Test
    void scrolledBy_clampsAtMax() {
        ScrollState s = ScrollState.of(100, 10).scrolledBy(9999);
        assertEquals(90, s.offset());
    }

    @Test
    void scrolledBy_positiveMovesDown() {
        ScrollState s = ScrollState.of(100, 10).scrolledBy(5);
        assertEquals(5, s.offset());
    }

    @Test
    void scrolledToTop() {
        ScrollState s = ScrollState.of(100, 10).scrolledBy(50).scrolledToTop();
        assertEquals(0, s.offset());
    }

    @Test
    void scrolledToBottom() {
        ScrollState s = ScrollState.of(100, 10).scrolledToBottom();
        assertEquals(90, s.offset());
    }

    @Test
    void canScrollUp_falseAtTop() {
        assertFalse(ScrollState.of(100, 10).canScrollUp());
    }

    @Test
    void canScrollUp_trueWhenScrolled() {
        assertTrue(ScrollState.of(100, 10).scrolledBy(5).canScrollUp());
    }

    @Test
    void canScrollDown_falseAtBottom() {
        assertFalse(ScrollState.of(100, 10).scrolledToBottom().canScrollDown());
    }

    @Test
    void visibleRange_atTop() {
        ScrollState s = ScrollState.of(100, 10);
        assertEquals(0, s.firstVisibleIndex());
        assertEquals(10, s.lastVisibleIndexExclusive());
    }

    @Test
    void visibleCount_partialLastPage() {
        // 25 items, 10 visible, scrolled to bottom -> offset 15, shows 15..25 = 10.
        // But test a genuinely partial case: 13 items, 10 visible.
        ScrollState s = ScrollState.of(13, 10).scrolledToBottom();
        // maxOffset = 3, visible 3..13 = 10 rows.
        assertEquals(3, s.firstVisibleIndex());
        assertEquals(13, s.lastVisibleIndexExclusive());
        assertEquals(10, s.visibleCount());
    }

    @Test
    void visibleCount_fewerItemsThanRows() {
        ScrollState s = ScrollState.of(4, 10);
        assertEquals(4, s.visibleCount());
    }

    @Test
    void isScrollable() {
        assertTrue(ScrollState.of(100, 10).isScrollable());
        assertFalse(ScrollState.of(10, 10).isScrollable());
        assertFalse(ScrollState.of(3, 10).isScrollable());
    }

    @Test
    void thumbSize_fullWhenEverythingFits() {
        assertEquals(1.0, ScrollState.of(5, 10).thumbSize());
    }

    @Test
    void thumbSize_fractionWhenScrollable() {
        assertEquals(0.1, ScrollState.of(100, 10).thumbSize(), 1e-9);
    }

    @Test
    void thumbPosition_zeroAtTop() {
        assertEquals(0.0, ScrollState.of(100, 10).thumbPosition());
    }

    @Test
    void thumbPosition_oneAtBottom() {
        assertEquals(1.0, ScrollState.of(100, 10).scrolledToBottom().thumbPosition(), 1e-9);
    }

    @Test
    void withTotalItems_reclampsOffsetWhenShrinking() {
        // Scrolled near the bottom of a big list, then the list shrinks.
        ScrollState s = ScrollState.of(100, 10).scrolledBy(90); // offset 90
        ScrollState shrunk = s.withTotalItems(20); // maxOffset now 10
        assertEquals(10, shrunk.offset());
    }

    @Test
    void constructor_negativeTotalItems_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ScrollState(-1, 10, 0));
    }

    @Test
    void constructor_zeroVisibleRows_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ScrollState(10, 0, 0));
    }

    @Test
    void constructor_clampsOffsetIntoRange() {
        // Direct construction with an out-of-range offset is clamped.
        assertEquals(90, new ScrollState(100, 10, 9999).offset());
        assertEquals(0, new ScrollState(100, 10, -50).offset());
    }
}