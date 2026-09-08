/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bridges a declarative {@link MultiblockPattern} to the {@link IMultiblockGeometry} contract consumed by
 * {@link AbstractMultiblockControllerBlock}. Cells marked {@link MultiblockPattern#IGNORE_CHAR} are excluded from
 * all position lists and the block count, so they behave as "no block required" without occupying a footprint cell.
 *
 * <p>Coordinate mapping: the pattern is defined in a canonical NORTH-facing frame where the x-axis runs clockwise
 * (right of the controller) and the z-axis runs away from the controller (depth/back). When the structure is placed
 * facing a direction other than NORTH, each local (dx, dz) offset is rotated via {@link Rotation#transform} before
 * being applied to the controller world position, producing the same footprint in all four horizontal orientations.
 */
public final class MultiblockPatternGeometry implements IMultiblockGeometry {

    private final MultiblockPattern pattern;
    private final int cachedBlockCount;

    public MultiblockPatternGeometry(final MultiblockPattern pattern) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.cachedBlockCount = countNonIgnore(pattern);
    }

    @Override
    public List<BlockPos> allPositions(final BlockPos controller, final Direction facing) {
        return collect(controller, facing, false);
    }

    @Override
    public List<BlockPos> partPositions(final BlockPos controller, final Direction facing) {
        return collect(controller, facing, true);
    }

    @Override
    public int blockCount() {
        return cachedBlockCount;
    }

    // internals

    private List<BlockPos> collect(final BlockPos controller, final Direction facing,
                                   final boolean excludeController) {
        final Rotation rotation = fromDirection(facing);
        final int cx = pattern.controllerX();
        final int cy = pattern.controllerY();
        final int cz = pattern.controllerZ();
        final List<BlockPos> result = new ArrayList<>(cachedBlockCount);
        for (int y = 0; y < pattern.sizeY(); y++) {
            for (int z = 0; z < pattern.sizeZ(); z++) {
                for (int x = 0; x < pattern.sizeX(); x++) {
                    if (pattern.charAt(x, y, z) == MultiblockPattern.IGNORE_CHAR) {
                        continue;
                    }
                    if (excludeController && x == cx && y == cy && z == cz) {
                        continue;
                    }
                    final int[] rotated = rotation.transform(x - cx, z - cz);
                    result.add(controller.offset(rotated[0], y - cy, rotated[1]));
                }
            }
        }
        return result;
    }

    private static int countNonIgnore(final MultiblockPattern p) {
        int n = 0;
        for (int y = 0; y < p.sizeY(); y++) {
            for (int z = 0; z < p.sizeZ(); z++) {
                for (int x = 0; x < p.sizeX(); x++) {
                    if (p.charAt(x, y, z) != MultiblockPattern.IGNORE_CHAR) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static Rotation fromDirection(final Direction facing) {
        return switch (facing) {
            case NORTH -> Rotation.NORTH;
            case EAST  -> Rotation.EAST;
            case SOUTH -> Rotation.SOUTH;
            case WEST  -> Rotation.WEST;
            default    -> throw new IllegalArgumentException(
                    "MultiblockPatternGeometry requires a horizontal direction; got " + facing);
        };
    }
}
