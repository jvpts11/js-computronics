/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ThisPcLayoutTest {

    @Test
    public void layout_isCleanAtEverySize() {
        for (final int[] size : new int[][]{{ThisPcLayout.MIN_W, ThisPcLayout.MIN_H},
                {ThisPcLayout.DEFAULT_W, ThisPcLayout.DEFAULT_H}, {420, 300}}) {
            final GuiLayout l = ThisPcLayout.layout(size[0], size[1]);
            assertTrue(l.isClean(), size[0] + "x" + size[1] + ": " + l.overlaps() + " " + l.outOfBounds());
        }
    }

    @Test
    public void driveRow_textNeverRunsUnderTheButtons() {
        for (final int width : new int[]{ThisPcLayout.MIN_W, ThisPcLayout.DEFAULT_W, 420}) {
            for (int buttons = 0; buttons <= 3; buttons++) {
                final GuiLayout l = ThisPcLayout.driveRow(width, buttons);
                assertTrue(l.isClean(), width + " with " + buttons + ": " + l.overlaps() + " " + l.outOfBounds());
            }
        }
    }

    @Test
    public void driveRow_keepsRoomForTheTextAtTheMinimumWidthWithThreeButtons() {
        // Three buttons on the narrowest window still leave a name's worth of room.
        assertTrue(ThisPcLayout.driveTextMaxW(ThisPcLayout.MIN_W, 3) >= 100);
    }

    @Test
    public void buttons_areRightAlignedInOrder() {
        final int width = ThisPcLayout.DEFAULT_W;
        assertTrue(ThisPcLayout.buttonX(width, 0, 3) < ThisPcLayout.buttonX(width, 1, 3));
        assertTrue(ThisPcLayout.buttonX(width, 1, 3) < ThisPcLayout.buttonX(width, 2, 3));
        assertEquals(width - 3, ThisPcLayout.buttonX(width, 2, 3) + ThisPcLayout.BTN_W);
    }

    @Test
    public void programGrid_tilesWithoutTouching() {
        for (final int width : new int[]{ThisPcLayout.MIN_W, ThisPcLayout.DEFAULT_W, 420}) {
            final GuiLayout l = ThisPcLayout.programGrid(width, 7);
            assertTrue(l.isClean(), width + ": " + l.overlaps() + " " + l.outOfBounds());
            assertTrue(ThisPcLayout.programColumns(width) >= 4, "at least four columns at " + width);
        }
    }
}
