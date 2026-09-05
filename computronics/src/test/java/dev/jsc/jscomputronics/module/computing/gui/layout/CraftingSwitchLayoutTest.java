/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CraftingSwitchLayoutTest {

    @Test
    void layout_hasNoOverlapsOrOverflow() {
        final GuiLayout l = CraftingSwitchLayout.layout();
        assertTrue(l.isClean(),
                "Crafting Switch layout is not clean: overlaps=" + l.overlaps()
                        + " outOfBounds=" + l.outOfBounds());
    }

    @Test
    void layout_fitsTheScreenHeightBudget() {
        assertTrue(CraftingSwitchLayout.HEIGHT <= 256,
                "switch screen must fit the GUI height budget; got " + CraftingSwitchLayout.HEIGHT);
    }
}
