/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jsc.jscomputronics.common.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

class DatacenterStationLayoutTest {

    @Test
    void layout_isClean() {
        final GuiLayout l = DatacenterStationLayout.layout();
        assertTrue(l.overlaps().isEmpty(), "overlaps: " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "out of bounds: " + l.outOfBounds());
    }
}
