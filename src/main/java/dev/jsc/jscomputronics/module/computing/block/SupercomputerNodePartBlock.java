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
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A structural section of the Supercomputer Node tower — the mid compute section or the vented cap.
 */
public class SupercomputerNodePartBlock extends Block implements DataNetworkConnectable {

    public static final MapCodec<SupercomputerNodePartBlock> CODEC =
            simpleCodec(SupercomputerNodePartBlock::new);

    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public static final DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public SupercomputerNodePartBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(TOP, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<SupercomputerNodePartBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        return java.util.Set.of(DataTier.HPC);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, FACING);
    }

    @org.jetbrains.annotations.Nullable
    public static BlockPos controllerBelow(final Level level, final BlockPos part) {
        for (int down = 1; down < SupercomputerNodeBlock.HEIGHT; down++) {
            final BlockPos candidate = part.below(down);
            if (level.getBlockState(candidate).getBlock() instanceof SupercomputerNodeBlock) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        final BlockPos controller = controllerBelow(level, pos);
        if (controller != null) {
            return level.getBlockState(controller).useWithoutItem(level, player,
                    new BlockHitResult(hit.getLocation(), hit.getDirection(), controller, false));
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            final BlockPos controller = controllerBelow(level, pos);
            if (controller != null
                    && level.getBlockEntity(controller) instanceof SupercomputerNodeBlockEntity) {
                level.destroyBlock(controller, true); // the controller drops its hardware
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
