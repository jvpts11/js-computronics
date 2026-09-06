/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.gui.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

class BusLayoutTest {

    @Test
    void layout_hasNoSolidOverlaps() {
        final GuiLayout l = BusLayout.layout();
        assertTrue(l.overlaps().isEmpty(), "overlapping elements: " + l.overlaps());
    }

    @Test
    void layout_keepsEveryElementInBounds() {
        final GuiLayout l = BusLayout.layout();
        assertTrue(l.outOfBounds().isEmpty(), "out-of-bounds elements: " + l.outOfBounds());
    }

    @Test
    void layout_isClean() {
        assertTrue(BusLayout.layout().isClean());
    }
}
