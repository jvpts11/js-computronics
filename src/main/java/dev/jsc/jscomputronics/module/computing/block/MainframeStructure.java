/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import dev.jsc.jscomputronics.common.multiblock.MultiblockGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the Mainframe multiblock: a 3-wide, 2-tall, 2-deep box (12 blocks).
 */
public final class MainframeStructure {

    public static final int WIDTH = 3;
    public static final int HEIGHT = 2;
    public static final int DEPTH = 2;
    public static final int BLOCK_COUNT = WIDTH * HEIGHT * DEPTH; // 12

    /** This structure's footprint as a value, so a controller can hand it to the shared multiblock lifecycle. */
    public static final MultiblockGeometry GEOMETRY = new MultiblockGeometry() {
        @Override
        public List<BlockPos> allPositions(final BlockPos controller, final Direction facing) {
            return MainframeStructure.allPositions(controller, facing);
        }

        @Override
        public List<BlockPos> partPositions(final BlockPos controller, final Direction facing) {
            return MainframeStructure.partPositions(controller, facing);
        }

        @Override
        public int blockCount() {
            return BLOCK_COUNT;
        }
    };

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
