/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.HbwInterfaceBlock;
import dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * The Supercomputer Console: the cluster's monitoring kiosk, the Datacenter Station's sibling.
 */
public class SupercomputerConsoleBlockEntity extends BlockEntity {

    private static final int SEARCH_LIMIT = 32;

    public SupercomputerConsoleBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.SUPERCOMPUTER_CONSOLE_BE.get(), pos, state);
    }

    @Nullable
    public HbwInterfaceBlockEntity findInterface() {
        final Level lvl = getLevel();
        if (lvl == null) {
            return null;
        }
        final Set<BlockPos> visited = new HashSet<>();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        int steps = 0;
        while (!queue.isEmpty() && steps++ < SEARCH_LIMIT) {
            final BlockPos current = queue.poll();
            for (final Direction direction : Direction.values()) {
                final BlockPos neighbor = current.relative(direction);
                if (!visited.add(neighbor)) {
                    continue;
                }
                final BlockState state = lvl.getBlockState(neighbor);
                if (state.getBlock() instanceof HbwInterfaceBlock
                        && lvl.getBlockEntity(neighbor) instanceof HbwInterfaceBlockEntity found) {
                    return found;
                }
                if (state.getBlock() instanceof SupercomputerNodeBlock
                        || state.getBlock() instanceof dev.jsc.jscomputronics.module.computing.block
                                .SupercomputerNodePartBlock
                        || (state.getBlock() instanceof dev.jsc.jscomputronics.module.computing.block
                                .DataCableBlock cable
                                && cable.tier() == dev.jsc.jscomputronics.common.network.DataTier.HPC)) {
                    queue.add(neighbor); // the HPC fabric: cables and node towers
                }
            }
        }
        return null;
    }
}
