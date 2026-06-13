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
import dev.jsc.jscomputronics.common.util.BlockDrops;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodePartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * The Supercomputer Node cabinet controller. The cabinet shares the Server Rack footprint
 * (2 wide, 3 tall, 2 deep) so the two stand side by side in a datacenter aisle.
 */
public class SupercomputerNodeBlock extends HorizontalDirectionalBlock
        implements EntityBlock, DataNetworkConnectable {

    public static final MapCodec<SupercomputerNodeBlock> CODEC = simpleCodec(SupercomputerNodeBlock::new);

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
    public boolean connectsOnFace(final BlockState state, final Direction face) {
        // A Supercomputer node takes its HPC cable on the rear only, like the other computers.
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction facing = context.getHorizontalDirection().getOpposite();
        final Level level = context.getLevel();
        for (final BlockPos part : ServerRackStructure.partPositions(context.getClickedPos(), facing)) {
            if (!level.getBlockState(part).canBeReplaced()) {
                return null; // no room for the cabinet — cancel placement, item not consumed
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state,
                            @Nullable final LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        final Direction facing = state.getValue(FACING);
        final boolean server = !level.isClientSide();
        for (final BlockPos part : ServerRackStructure.partPositions(pos, facing)) {
            level.setBlock(part, ComputingModule.SUPERCOMPUTER_NODE_PART.get().defaultBlockState()
                    .setValue(SupercomputerNodePartBlock.TOP, ServerRackStructure.isTopLayer(pos, part))
                    .setValue(SupercomputerNodePartBlock.FRONT,
                            ServerRackStructure.isFrontBayBlock(pos, facing, part))
                    .setValue(SupercomputerNodePartBlock.FACING, facing),
                    Block.UPDATE_ALL);
            if (server && level.getBlockEntity(part) instanceof SupercomputerNodePartBlockEntity partBe) {
                partBe.setController(pos);
            }
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
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        // Drops happen here (not in dissolve) so creative mode never spills the node or its hardware.
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild) {
            dropContents(serverLevel, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof SupercomputerNodeBlockEntity node) {
                node.onBroken(serverLevel);
            }
            dissolve(serverLevel, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    static void dropContents(final ServerLevel level, final BlockPos controllerPos) {
        Block.popResource(level, controllerPos, new ItemStack(ComputingModule.SUPERCOMPUTER_NODE_ITEM.get()));
        if (level.getBlockEntity(controllerPos) instanceof SupercomputerNodeBlockEntity node) {
            BlockDrops.spill(level, controllerPos, node.getHardware());
        }
    }

    private static final java.util.Set<BlockPos> DISSOLVING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void dissolve(final ServerLevel level, final BlockPos controllerPos, final Direction facing) {
        if (!DISSOLVING.add(controllerPos.immutable())) {
            return;
        }
        try {
            for (final BlockPos part : ServerRackStructure.partPositions(controllerPos, facing)) {
                if (level.getBlockState(part).getBlock() instanceof SupercomputerNodePartBlock) {
                    level.removeBlock(part, false);
                }
            }
            if (level.getBlockState(controllerPos).getBlock() instanceof SupercomputerNodeBlock) {
                level.removeBlock(controllerPos, false);
            }
        } finally {
            DISSOLVING.remove(controllerPos);
        }
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
