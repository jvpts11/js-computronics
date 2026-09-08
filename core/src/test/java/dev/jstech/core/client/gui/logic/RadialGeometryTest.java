/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.client.gui.logic;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialGeometryTest {

    private static final double EPS = 1e-9;

    @Test
    void segmentAtAngle_topIsZero() {
        assertEquals(0, RadialGeometry.segmentAtAngle(0.0, 4));
    }

    @Test
    void segmentAtAngle_fourQuadrants() {
        /*
         * 4 segments of π/2 each, starting at top, clockwise.
         * Pick angle in the MIDDLE of each segment to avoid boundaries.
         */
        double q = Math.PI / 2;
        assertEquals(0, RadialGeometry.segmentAtAngle(q * 0.5, 4)); // top-right of top seg
        assertEquals(1, RadialGeometry.segmentAtAngle(q * 1.5, 4)); // right
        assertEquals(2, RadialGeometry.segmentAtAngle(q * 2.5, 4)); // bottom
        assertEquals(3, RadialGeometry.segmentAtAngle(q * 3.5, 4)); // left
    }

    @Test
    void segmentAtAngle_normalizesNegative() {
        // -π/4 normalizes to 7π/4, which is in the last segment of 4.
        assertEquals(3, RadialGeometry.segmentAtAngle(-Math.PI / 4, 4));
    }

    @Test
    void segmentAtAngle_wrapsAtTwoPi() {
        // Exactly 2π wraps to 0.
        assertEquals(0, RadialGeometry.segmentAtAngle(Math.PI * 2, 4));
    }

    @Test
    void segmentAtAngle_singleSegment_alwaysZero() {
        assertEquals(0, RadialGeometry.segmentAtAngle(1.234, 1));
        assertEquals(0, RadialGeometry.segmentAtAngle(5.0, 1));
    }

    @Test
    void segmentAtAngle_zeroSegments_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> RadialGeometry.segmentAtAngle(0.0, 0));
    }

    @Test
    void angleFromCenter_top() {
        // Directly above center: dx=0, dy=-1 -> angle 0.
        assertEquals(0.0, RadialGeometry.angleFromCenter(0, -1), EPS);
    }

    @Test
    void angleFromCenter_right() {
        // To the right: dx=1, dy=0 -> π/2.
        assertEquals(Math.PI / 2, RadialGeometry.angleFromCenter(1, 0), EPS);
    }

    @Test
    void angleFromCenter_bottom() {
        // Below: dx=0, dy=1 -> π.
        assertEquals(Math.PI, RadialGeometry.angleFromCenter(0, 1), EPS);
    }

    @Test
    void angleFromCenter_left() {
        // To the left: dx=-1, dy=0 -> 3π/2.
        assertEquals(3 * Math.PI / 2, RadialGeometry.angleFromCenter(-1, 0), EPS);
    }

    @Test
    void distanceFromCenter_pythagoras() {
        assertEquals(5.0, RadialGeometry.distanceFromCenter(3, 4), EPS);
    }

    @Test
    void segmentAt_insideDeadzone_empty() {
        // Distance 2 with inner radius 5 -> dead zone.
        OptionalInt seg = RadialGeometry.segmentAt(0, -2, 4, 5, 50);
        assertTrue(seg.isEmpty());
    }

    @Test
    void segmentAt_outsideOuterRadius_empty() {
        OptionalInt seg = RadialGeometry.segmentAt(0, -100, 4, 5, 50);
        assertTrue(seg.isEmpty());
    }

    @Test
    void segmentAt_validRing_returnsSegment() {
        // Above center, within ring -> top segment (0).
        OptionalInt seg = RadialGeometry.segmentAt(0, -20, 4, 5, 50);
        assertEquals(OptionalInt.of(0), seg);
    }

    @Test
    void segmentAt_rightSide_returnsSegmentOne() {
        OptionalInt seg = RadialGeometry.segmentAt(20, 0, 4, 5, 50);
        assertEquals(OptionalInt.of(1), seg);
    }
}
