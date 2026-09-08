/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.client.gui.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphScaleTest {

    private static final double EPS = 1e-9;

    @Test
    void normalize_midpoint() {
        GraphScale s = new GraphScale(0, 100);
        assertEquals(0.5, s.normalize(50), EPS);
    }

    @Test
    void normalize_minIsZero() {
        GraphScale s = new GraphScale(0, 100);
        assertEquals(0.0, s.normalize(0), EPS);
    }

    @Test
    void normalize_maxIsOne() {
        GraphScale s = new GraphScale(0, 100);
        assertEquals(1.0, s.normalize(100), EPS);
    }

    @Test
    void normalize_degenerateDomain_returnsZero() {
        GraphScale s = new GraphScale(50, 50);
        assertEquals(0.0, s.normalize(50), EPS);
        assertEquals(0.0, s.normalize(999), EPS);
    }

    @Test
    void normalize_outsideDomain_notClamped() {
        GraphScale s = new GraphScale(0, 100);
        assertEquals(1.5, s.normalize(150), EPS);
        assertEquals(-0.5, s.normalize(-50), EPS);
    }

    @Test
    void normalizeClamped_belowMin() {
        GraphScale s = new GraphScale(0, 100);
        assertEquals(0.0, s.normalizeClamped(-50), EPS);
    }

    @Test
    void normalizeClamped_aboveMax() {
        GraphScale s = new GraphScale(0, 100);
        assertEquals(1.0, s.normalizeClamped(150), EPS);
    }

    @Test
    void valueToY_maxAtTop() {
        // Max value maps to y = 0 (top of graph).
        GraphScale s = new GraphScale(0, 100);
        assertEquals(0.0, s.valueToY(100, 200), EPS);
    }

    @Test
    void valueToY_minAtBottom() {
        // Min value maps to y = height (bottom).
        GraphScale s = new GraphScale(0, 100);
        assertEquals(200.0, s.valueToY(0, 200), EPS);
    }

    @Test
    void valueToY_midpoint() {
        GraphScale s = new GraphScale(0, 100);
        assertEquals(100.0, s.valueToY(50, 200), EPS);
    }

    @Test
    void valueToY_clampsOutOfDomain() {
        GraphScale s = new GraphScale(0, 100);
        // Above max clamps to top (0), below min clamps to bottom (height).
        assertEquals(0.0, s.valueToY(150, 200), EPS);
        assertEquals(200.0, s.valueToY(-50, 200), EPS);
    }

    @Test
    void fromData_findsMinMax() {
        GraphScale s = GraphScale.fromData(new double[]{3, 7, 1, 9, 4});
        assertEquals(1.0, s.minValue(), EPS);
        assertEquals(9.0, s.maxValue(), EPS);
    }

    @Test
    void fromData_singleValue() {
        GraphScale s = GraphScale.fromData(new double[]{42});
        assertEquals(42.0, s.minValue(), EPS);
        assertEquals(42.0, s.maxValue(), EPS);
    }

    @Test
    void fromData_empty_returnsDegenerate() {
        GraphScale s = GraphScale.fromData(new double[]{});
        assertEquals(0.0, s.minValue(), EPS);
        assertEquals(0.0, s.maxValue(), EPS);
    }

    @Test
    void constructor_maxLessThanMin_throws() {
        assertThrows(IllegalArgumentException.class, () -> new GraphScale(100, 0));
    }
}