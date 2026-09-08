/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.multiblock;

/**
 * Horizontal rotation of a multiblock structure relative to its canonical (NORTH-facing) layout.
 */
public enum Rotation {

    NORTH,
    EAST,
    SOUTH,
    WEST;

    public int[] transform(int x, int z) {
        return switch (this) {
            case NORTH -> new int[] { x, z };
            case EAST  -> new int[] { -z, x };
            case SOUTH -> new int[] { -x, -z };
            case WEST  -> new int[] { z, -x };
        };
    }

    public Rotation clockwise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
        };
    }

    public Rotation counterClockwise() {
        return switch (this) {
            case NORTH -> WEST;
            case EAST -> NORTH;
            case SOUTH -> EAST;
            case WEST -> SOUTH;
        };
    }
}
