/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block;

import dev.jstech.core.multiblock.BlockMatcher;
import dev.jstech.core.multiblock.MultiblockPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the Mainframe multiblock: a 3-wide, 2-tall, 2-deep box (12 blocks).
 *
 * <p>The canonical NORTH-facing layout (x = clockwise from controller, z = depth):
 * <pre>
 *   y=0  z=0 (front): P # P
 *        z=1 (back):  P P P
 *   y=1  z=0 (front): P P P
 *        z=1 (back):  P P P
 * </pre>
 * The controller is at the middle column (x=1), ground floor (y=0), front row (z=0).
 */
public final class MainframeStructure {

    public static final int WIDTH = 3;
    public static final int HEIGHT = 2;
    public static final int DEPTH = 2;
    public static final int BLOCK_COUNT = WIDTH * HEIGHT * DEPTH; // 12

    /**
     * Declarative description of the Mainframe footprint, used by
     * {@link dev.jstech.core.multiblock.MultiblockPatternGeometry} to compute
     * world positions for any of the four horizontal orientations.
     */
    public static final MultiblockPattern PATTERN = MultiblockPattern.builder("mainframe")
            .layer("P#P", "PPP")
            .layer("PPP", "PPP")
            .where('P', BlockMatcher.any())
            .build();

    private MainframeStructure() {
    }

    public static List<BlockPos> allPositions(final BlockPos controller, final Direction facing) {
        final Direction right = facing.getClockWise();
        final Direction back = facing.getOpposite();
        final List<BlockPos> positions = new ArrayList<>(BLOCK_COUNT);
        for (int w = -1; w <= 1; w++) {
            for (int h = 0; h < HEIGHT; h++) {
                for (int d = 0; d < DEPTH; d++) {
                    positions.add(controller.relative(right, w).above(h).relative(back, d));
                }
            }
        }
        return positions;
    }

    public static List<BlockPos> partPositions(final BlockPos controller, final Direction facing) {
        final List<BlockPos> positions = allPositions(controller, facing);
        positions.removeIf(pos -> pos.equals(controller));
        return positions;
    }

    public static boolean isCentralColumn(final BlockPos controller, final Direction facing, final BlockPos part) {
        final Direction right = facing.getClockWise();
        final int w = (part.getX() - controller.getX()) * right.getStepX()
                + (part.getZ() - controller.getZ()) * right.getStepZ();
        return w == 0;
    }
}
