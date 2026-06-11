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
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Supercomputer Node tower controller.
 */
public class SupercomputerNodeBlock extends HorizontalDirectionalBlock
        implements EntityBlock, DataNetworkConnectable {

    public static final MapCodec<SupercomputerNodeBlock> CODEC = simpleCodec(SupercomputerNodeBlock::new);

    public static final int HEIGHT = 3;

    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public SupercomputerNodeBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FILLED, false));
    }

    @Override
    protected MapCodec<SupercomputerNodeBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        return java.util.Set.of(DataTier.HPC); // the cluster fabric is HPC-only
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILLED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Level level = context.getLevel();
        for (int h = 1; h < HEIGHT; h++) {
            if (!level.getBlockState(context.getClickedPos().above(h)).canBeReplaced()) {
                return null; // no room for the tower — cancel, the item is not consumed
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state,
                            @Nullable final LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        for (int h = 1; h < HEIGHT; h++) {
            level.setBlock(pos.above(h), ComputingModule.SUPERCOMPUTER_NODE_PART.get().defaultBlockState()
                    .setValue(SupercomputerNodePartBlock.TOP, h == HEIGHT - 1)
                    .setValue(SupercomputerNodePartBlock.FACING, state.getValue(FACING)),
                    Block.UPDATE_ALL);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SupercomputerNodeBlockEntity node) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new dev.jsc.jscomputronics.module.computing.menu
                                    .SupercomputerNodeMenu(id, inventory, node),
                            Component.translatable("block.jsc.supercomputer_node")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && level.getBlockEntity(pos) instanceof SupercomputerNodeBlockEntity node) {
                node.onBroken(serverLevel);
            }
            for (int h = 1; h < HEIGHT; h++) {
                if (level.getBlockState(pos.above(h)).getBlock() instanceof SupercomputerNodePartBlock) {
                    level.setBlock(pos.above(h), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new SupercomputerNodeBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ComputingModule.SUPERCOMPUTER_NODE_BE.get(),
                SupercomputerNodeBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <A extends BlockEntity, E extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            final BlockEntityType<A> given, final BlockEntityType<E> expected,
            final BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
