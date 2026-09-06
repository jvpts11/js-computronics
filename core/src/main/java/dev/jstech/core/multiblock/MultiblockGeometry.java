/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * The footprint of one multiblock structure as a value, decoupled from any one controller. A controller hands its
 * geometry to the shared lifecycle so the raise/dissolve loop never has to know which structure it is operating on; a
 * new multiblock only has to supply its own implementation. Exposing the footprint this way (rather than through static
 * calls to a specific structure class) is what lets a single base class drive every controller.
 */
public interface MultiblockGeometry {

    /** Every cell the structure occupies, controller included, when the controller is at {@code controller}. */
    List<BlockPos> allPositions(BlockPos controller, Direction facing);

    /** Every cell except the controller's own — the part blocks the controller raises and later dissolves. */
    List<BlockPos> partPositions(BlockPos controller, Direction facing);

    /** Total number of cells in the footprint (controller plus parts). */
    int blockCount();
}
