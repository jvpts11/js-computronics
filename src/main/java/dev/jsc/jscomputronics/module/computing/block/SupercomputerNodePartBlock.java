/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jsc.jscomputronics.common.network.RearFacingDataPort;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodePartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A structural part of the Supercomputer Node cabinet.
 */
public class SupercomputerNodePartBlock extends Block implements EntityBlock, RearFacingDataPort {

    public static final MapCodec<SupercomputerNodePartBlock> CODEC =
            simpleCodec(SupercomputerNodePartBlock::new);

    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public static final BooleanProperty FRONT = BooleanProperty.create("front");

    public static final BooleanProperty FILLED = SupercomputerNodeBlock.FILLED;

    public static final DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public SupercomputerNodePartBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(TOP, false)
                .setValue(FRONT, false)
                .setValue(FILLED, false)
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
        builder.add(TOP, FRONT, FILLED, FACING);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new SupercomputerNodePartBlockEntity(pos, state);
    }

    @Nullable
    public static BlockPos controllerOf(final Level level, final BlockPos part) {
        return level.getBlockEntity(part) instanceof SupercomputerNodePartBlockEntity partBe
                ? partBe.controllerPos() : null;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        final BlockPos controller = controllerOf(level, pos);
        if (controller != null && level.getBlockState(controller).getBlock() instanceof SupercomputerNodeBlock) {
            return level.getBlockState(controller).useWithoutItem(level, player,
                    new BlockHitResult(hit.getLocation(), hit.getDirection(), controller, false));
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        // Drops happen here (not in dissolve) so creative mode never spills the node or its hardware.
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild) {
            final BlockPos controller = controllerOf(level, pos);
            if (controller != null
                    && level.getBlockState(controller).getBlock()
                            instanceof dev.jsc.jscomputronics.common.multiblock.AbstractMultiblockControllerBlock owner) {
                owner.dropContentsExternally(serverLevel, controller);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            final BlockPos controller = controllerOf(level, pos);
            if (controller != null
                    && level.getBlockState(controller).getBlock()
                            instanceof dev.jsc.jscomputronics.common.multiblock.AbstractMultiblockControllerBlock owner) {
                // Take the whole cabinet down with non-dropping removals; the player-break path above
                // already spilled the node and its hardware.
                owner.dissolve(serverLevel, controller,
                        level.getBlockState(controller).getValue(HorizontalDirectionalBlock.FACING));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
