/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the Server Rack multiblock: a 2-wide, 3-tall, 2-deep cabinet (12 blocks) that is logically a single rack.
 */
public final class ServerRackStructure {

    public static final int WIDTH = 2;
    public static final int HEIGHT = 3;
    public static final int DEPTH = 2;
    public static final int BLOCK_COUNT = WIDTH * HEIGHT * DEPTH; // 12

    private ServerRackStructure() {
    }

    public static List<BlockPos> allPositions(final BlockPos controller, final Direction facing) {
        final Direction right = facing.getClockWise();
        final Direction back = facing.getOpposite();
        final List<BlockPos> positions = new ArrayList<>(BLOCK_COUNT);
        for (int w = 0; w < WIDTH; w++) {
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

    public static boolean isTopLayer(final BlockPos controller, final BlockPos part) {
        return part.getY() - controller.getY() == HEIGHT - 1;
    }

    public static boolean isFrontBayBlock(final BlockPos controller, final Direction facing,
                                          final BlockPos part) {
        final int h = part.getY() - controller.getY();
        if (h < 0 || h >= HEIGHT - 1) {
            return false;
        }
        // The front layer is the controller's own layer along the facing axis: the
        // controller column and the column one step clockwise of it.
        final BlockPos flat = part.below(h);
        return flat.equals(controller) || flat.equals(controller.relative(facing.getClockWise()));
    }

    public static BlockPos bayBlockPos(final BlockPos controller, final Direction facing,
                                       final int w, final int h) {
        return controller.relative(facing.getClockWise(), w).above(h);
    }
}
