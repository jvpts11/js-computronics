/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RotationTest {

    @Test
    void values_hasFourRotations() {
        assertEquals(4, Rotation.values().length);
    }

    @Test
    void north_isIdentity() {
        assertArrayEquals(new int[] { 0, 0 }, Rotation.NORTH.transform(0, 0));
        assertArrayEquals(new int[] { 1, 0 }, Rotation.NORTH.transform(1, 0));
        assertArrayEquals(new int[] { 0, 1 }, Rotation.NORTH.transform(0, 1));
        assertArrayEquals(new int[] { -1, -1 }, Rotation.NORTH.transform(-1, -1));
        assertArrayEquals(new int[] { 5, -3 }, Rotation.NORTH.transform(5, -3));
    }

    @Test
    void east_rotates90Clockwise() {
        // (1, 0) "east of controller in pattern" → (0, 1) "south in world" when facing east
        assertArrayEquals(new int[] { 0, 1 }, Rotation.EAST.transform(1, 0));
        // (0, 1) "south in pattern" → (-1, 0) "west in world"
        assertArrayEquals(new int[] { -1, 0 }, Rotation.EAST.transform(0, 1));
    }

    @Test
    void south_rotates180() {
        assertArrayEquals(new int[] { -1, 0 }, Rotation.SOUTH.transform(1, 0));
        assertArrayEquals(new int[] { 0, -1 }, Rotation.SOUTH.transform(0, 1));
        assertArrayEquals(new int[] { -3, 5 }, Rotation.SOUTH.transform(3, -5));
    }

    @Test
    void west_rotates270Clockwise() {
        assertArrayEquals(new int[] { 0, -1 }, Rotation.WEST.transform(1, 0));
        assertArrayEquals(new int[] { 1, 0 }, Rotation.WEST.transform(0, 1));
    }

    @Test
    void fourRotations_returnToOrigin() {
        // (1, 0) → EAST → (0, 1) → EAST → (-1, 0) → EAST → (0, -1) → EAST → (1, 0)
        int x = 3, z = 2;
        int[] n = Rotation.NORTH.transform(x, z);
        int[] e = Rotation.EAST.transform(x, z);
        int[] s = Rotation.SOUTH.transform(x, z);
        int[] w = Rotation.WEST.transform(x, z);
        assertArrayEquals(new int[] { x, z }, n);
        assertArrayEquals(new int[] { -z, x }, e);
        assertArrayEquals(new int[] { -x, -z }, s);
        assertArrayEquals(new int[] { z, -x }, w);
    }

    @Test
    void clockwise_cyclesNESW() {
        assertSame(Rotation.EAST, Rotation.NORTH.clockwise());
        assertSame(Rotation.SOUTH, Rotation.EAST.clockwise());
        assertSame(Rotation.WEST, Rotation.SOUTH.clockwise());
        assertSame(Rotation.NORTH, Rotation.WEST.clockwise());
    }

    @Test
    void counterClockwise_cyclesNWSE() {
        assertSame(Rotation.WEST, Rotation.NORTH.counterClockwise());
        assertSame(Rotation.SOUTH, Rotation.WEST.counterClockwise());
        assertSame(Rotation.EAST, Rotation.SOUTH.counterClockwise());
        assertSame(Rotation.NORTH, Rotation.EAST.counterClockwise());
    }

    @Test
    void clockwise_thenCounterClockwise_isIdentity() {
        for (var rot : Rotation.values()) {
            assertSame(rot, rot.clockwise().counterClockwise());
            assertSame(rot, rot.counterClockwise().clockwise());
        }
    }
}
