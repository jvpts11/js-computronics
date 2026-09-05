/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

class ServerRouterLayoutTest {

    @Test
    void layout_isCleanAtMaxSections() {
        final GuiLayout l = ServerRouterLayout.layout(ServerRouterLayout.MAX_SECTIONS);
        assertTrue(l.overlaps().isEmpty(), "overlaps: " + l.overlaps());
        assertTrue(l.outOfBounds().isEmpty(), "out of bounds: " + l.outOfBounds());
    }

    @Test
    void layout_isCleanWithNoSections() {
        assertTrue(ServerRouterLayout.layout(0).isClean());
    }
}
