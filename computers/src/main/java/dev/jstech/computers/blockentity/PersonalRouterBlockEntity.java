/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.blockentity;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.block.DataCableBlock;
import dev.jstech.core.network.ConnectivityIndex;
import dev.jstech.core.network.INetworkBridge;
import dev.jstech.core.network.NetworkSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * BlockEntity backing the Personal Router.
 */
public class PersonalRouterBlockEntity extends BlockEntity {

    public PersonalRouterBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PERSONAL_ROUTER_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            final ConnectivityIndex index = NetworkSystem.get(serverLevel).connectivity();
            final long encodedPos = worldPosition.asLong();
            if (!index.contains(encodedPos)) {
                index.onCablePlaced(encodedPos, bridgeNeighbors(serverLevel));
            }
        }
    }

    private Set<Long> bridgeNeighbors(final ServerLevel serverLevel) {
        final Set<Long> neighbors = new HashSet<>();
        for (final Direction direction : Direction.values()) {
            final BlockPos neighborPos = worldPosition.relative(direction);
            final var block = serverLevel.getBlockState(neighborPos).getBlock();
            if (block instanceof DataCableBlock || block instanceof INetworkBridge) {
                neighbors.add(neighborPos.asLong());
            }
        }
        return neighbors;
    }
}
