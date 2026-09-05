/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.logic;

import java.util.OptionalInt;

/**
 * Stateless geometry for radial (pie) menus.
 */
public final class RadialGeometry {

    private RadialGeometry() {
    }

    private static final double TWO_PI = Math.PI * 2.0;

    public static int segmentAtAngle(final double angleRadians, final int segmentCount) {
        if (segmentCount < 1) {
            throw new IllegalArgumentException(
                    "segmentCount must be >= 1; got " + segmentCount);
        }
        double a = angleRadians % TWO_PI;
        if (a < 0) {
            a += TWO_PI;
        }
        final double segmentSize = TWO_PI / segmentCount;
        int index = (int) Math.floor(a / segmentSize);
        if (index >= segmentCount) {
            index = segmentCount - 1; // guard the exact-2π boundary
        }
        return index;
    }

    public static double angleFromCenter(final double dx, final double dy) {
        // atan2(dx, -dy): top (0,-1) -> 0; right (1,0) -> π/2;
        // bottom (0,1) -> π; left (-1,0) -> 3π/2 after normalization.
        double a = Math.atan2(dx, -dy);
        if (a < 0) {
            a += TWO_PI;
        }
        return a;
    }

    public static double distanceFromCenter(final double dx, final double dy) {
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static OptionalInt segmentAt(
            final double dx,
            final double dy,
            final int segmentCount,
            final double innerRadius,
            final double outerRadius) {
        final double dist = distanceFromCenter(dx, dy);
        if (dist < innerRadius || dist > outerRadius) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(segmentAtAngle(angleFromCenter(dx, dy), segmentCount));
    }
}