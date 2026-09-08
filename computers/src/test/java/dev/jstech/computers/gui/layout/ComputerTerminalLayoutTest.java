/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

class ComputerTerminalLayoutTest {

    @Test
    void layout_isCleanForPersonalComputerHost() {
        final GuiLayout l = ComputerTerminalLayout.layout(false);
        assertTrue(l.overlaps().isEmpty(), "overlaps (PC): " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "out of bounds (PC): " + l.outOfBounds());
    }

    @Test
    void layout_isCleanForMainframeHost() {
        final GuiLayout l = ComputerTerminalLayout.layout(true);
        assertTrue(l.overlaps().isEmpty(), "overlaps (Mainframe): " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "out of bounds (Mainframe): " + l.outOfBounds());
    }
}
