/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.network.ConnectivityIndex;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * BlockEntity backing a {@link DataCableBlock}.
 */
public class DataCableBlockEntity extends BlockEntity {

    public DataCableBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.DATA_CABLE_BE.get(), pos, state);
    }

    public DataTier tier() {
        return getBlockState().getBlock() instanceof DataCableBlock cable
                ? cable.tier()
                : DataTier.T1_ETHERNET;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            final ConnectivityIndex index = NetworkSystem.get(serverLevel).connectivity();
            final long encodedPos = worldPosition.asLong();
            if (!index.contains(encodedPos)) {
                index.onCablePlaced(encodedPos, sameTierNeighbors(serverLevel));
            }
        }
    }

    private Set<Long> sameTierNeighbors(final ServerLevel serverLevel) {
        final Set<Long> neighbors = new HashSet<>();
        final DataTier myTier = tier();
        for (final Direction direction : Direction.values()) {
            final BlockPos neighborPos = worldPosition.relative(direction);
            if (serverLevel.getBlockState(neighborPos).getBlock() instanceof DataCableBlock other
                    && other.tier() == myTier) {
                neighbors.add(neighborPos.asLong());
            }
        }
        return neighbors;
    }
}
