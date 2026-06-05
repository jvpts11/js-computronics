/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A data network cable block.
 */
public class DataCableBlock extends PipeBlock implements EntityBlock {

    public static final MapCodec<DataCableBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("tier").forGetter(block -> block.tier.name()),
                    propertiesCodec()
            ).apply(instance, (tierName, props) -> new DataCableBlock(props, DataTier.valueOf(tierName))));

    private final DataTier tier;

    public DataCableBlock(final Properties properties, final DataTier tier) {
        super(0.1875F, properties);
        this.tier = tier;
        BlockState defaultState = stateDefinition.any();
        for (final BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
            defaultState = defaultState.setValue(property, false);
        }
        registerDefaultState(defaultState);
    }

    public DataTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (final Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                    connectsTo(context.getLevel(), context.getClickedPos(), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(final BlockState state, final Direction direction,
                                     final BlockState neighborState, final LevelAccessor level,
                                     final BlockPos pos, final BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                connectsTo(level, pos, direction));
    }

    private boolean connectsTo(final LevelAccessor level, final BlockPos pos, final Direction direction) {
        final var neighbor = level.getBlockState(pos.relative(direction)).getBlock();
        if (neighbor instanceof DataCableBlock other) {
            return other.tier == this.tier;
        }
        return neighbor instanceof dev.jsc.jscomputronics.common.network.DataNetworkConnectable;
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
        return new DataCableBlockEntity(pos, state);
    }
}
