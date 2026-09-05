/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FlowLayoutTest {

    @Test
    public void row_handsOutRectanglesLeftToRightWithTheGap() {
        final FlowLayout row = FlowLayout.row(10, 20, 3);
        assertArrayEquals(new int[] {10, 20, 30, 12}, row.next(30, 12));
        assertArrayEquals(new int[] {43, 20, 20, 12}, row.next(20, 12));
        assertEquals(66, row.x());
        assertEquals(20, row.y());
    }

    @Test
    public void column_handsOutRectanglesTopToBottomWithTheGap() {
        final FlowLayout column = FlowLayout.column(5, 5, 4);
        assertArrayEquals(new int[] {5, 5, 40, 10}, column.next(40, 10));
        assertArrayEquals(new int[] {5, 19, 40, 10}, column.next(40, 10));
        assertEquals(33, column.y());
    }

    @Test
    public void skip_leavesEmptySpaceBeforeTheNextRectangle() {
        final FlowLayout row = FlowLayout.row(0, 0, 2);
        row.next(10, 10);
        row.skip(6);
        assertArrayEquals(new int[] {18, 0, 10, 10}, row.next(10, 10));
    }
}
