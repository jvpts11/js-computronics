/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.computers.blockentity.PersonalRouterBlockEntity;
import dev.jstech.core.network.IDataNetworkConnectable;
import dev.jstech.core.network.INetworkBridge;
import dev.jstech.core.network.NetworkSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Personal Router: a simple, tier-less block that converts between Ethernet and HBW so a Personal Computer (Ethernet) can reach the HBW backbone.
 */
public class PersonalRouterBlock extends net.minecraft.world.level.block.HorizontalDirectionalBlock
        implements EntityBlock, IDataNetworkConnectable, INetworkBridge {

    public static final MapCodec<PersonalRouterBlock> CODEC = simpleCodec(PersonalRouterBlock::new);

    public PersonalRouterBlock(final Properties properties) {
        super(properties);
        // Facing is purely cosmetic (the status panel); cables still connect on every side.
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<PersonalRouterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            final net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(final net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public java.util.Set<dev.jstech.core.network.DataTier> acceptedCableTiers() {
        // The Personal Router bridges exactly the two access/backbone tiers it converts between.
        return java.util.Set.of(dev.jstech.core.network.DataTier.T1_ETHERNET,
                dev.jstech.core.network.DataTier.T2_HBW);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            NetworkSystem.get(serverLevel).connectivity().onCableRemoved(pos.asLong());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new PersonalRouterBlockEntity(pos, state);
    }
}
