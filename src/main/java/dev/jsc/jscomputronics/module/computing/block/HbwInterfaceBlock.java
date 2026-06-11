/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jsc.jscomputronics.common.network.DataNetworkConnectable;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The HBW Interface: the single point where a Supercomputer cluster meets the network.
 */
public class HbwInterfaceBlock extends Block implements EntityBlock, DataNetworkConnectable {

    public static final MapCodec<HbwInterfaceBlock> CODEC = simpleCodec(HbwInterfaceBlock::new);

    public HbwInterfaceBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<HbwInterfaceBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        // The backbone on one side, the cluster fabric on the other.
        return java.util.Set.of(DataTier.T2_HBW, DataTier.HPC);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof HbwInterfaceBlockEntity be) {
            be.onBroken(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new HbwInterfaceBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ComputingModule.HBW_INTERFACE_BE.get(),
                HbwInterfaceBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <A extends BlockEntity, E extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            final BlockEntityType<A> given, final BlockEntityType<E> expected,
            final BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
