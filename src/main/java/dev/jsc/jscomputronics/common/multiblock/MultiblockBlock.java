/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.List;

/**
 * A block that, when placed, expands into a fixed multiblock footprint (a controller plus its part blocks). Implementing this gives every such block a uniform way to report the world cells it occupies and whether it fits at a spot, so cross-cutting features — placement validation, a placement preview, structure highlighting — can work on any multiblock without knowing which one it is. Without it, those features have to hard-code one block's geometry and silently work for that block alone.
 */
public interface MultiblockBlock {

    /**
     * Every world position this multiblock occupies, the controller included, when its controller is
     * placed at {@code origin} facing {@code facing}.
     */
    List<BlockPos> footprint(BlockPos origin, Direction facing);

    /**
     * Whether the whole footprint can be placed at {@code origin} facing {@code facing}: every cell
     * other than the controller must currently be replaceable. The controller cell is excluded because
     * the placement context has already validated it.
     */
    default boolean canPlaceAt(final BlockGetter level, final BlockPos origin, final Direction facing) {
        for (final BlockPos cell : footprint(origin, facing)) {
            if (!cell.equals(origin) && !level.getBlockState(cell).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }
}
