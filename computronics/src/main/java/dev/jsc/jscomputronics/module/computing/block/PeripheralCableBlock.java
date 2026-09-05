/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.peripheral.PeripheralConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The Peripheral Cable (T1, {@code COMPUTING}): a short-range point-to-point link between a computer and a peripheral (Monitor, Drive, Printer).
 */
public class PeripheralCableBlock extends PipeBlock implements PeripheralConnectable {

    public static final MapCodec<PeripheralCableBlock> CODEC = simpleCodec(PeripheralCableBlock::new);

    public PeripheralCableBlock(final Properties properties) {
        // 0.1875 matches the 6px cable_core model, so the selection/collision box covers the
        super(0.1875F, properties);
        BlockState defaultState = stateDefinition.any();
        for (final BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
            defaultState = defaultState.setValue(property, false);
        }
        registerDefaultState(defaultState);
    }

    @Override
    public PeripheralCableType peripheralType() {
        return PeripheralCableType.COMPUTING;
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
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(level, pos, direction));
    }

    private boolean connectsTo(final LevelAccessor level, final BlockPos pos, final Direction direction) {
        final var neighbor = level.getBlockState(pos.relative(direction)).getBlock();
        return neighbor instanceof PeripheralConnectable connectable
                && connectable.peripheralType() == peripheralType();
    }
}
