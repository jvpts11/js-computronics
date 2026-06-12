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
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackPartBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Server Rack: a 2-wide, 3-tall, 2-deep multiblock cabinet that is logically a single rack.
 */
public class ServerRackBlock extends HorizontalDirectionalBlock
        implements EntityBlock, DataNetworkConnectable,
        dev.jsc.jscomputronics.common.multiblock.MultiblockBlock {

    public static final MapCodec<ServerRackBlock> CODEC = simpleCodec(ServerRackBlock::new);

    public static final net.minecraft.world.level.block.state.properties.IntegerProperty BAYS =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("bays", 0, 3);

    public ServerRackBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BAYS, 0));
    }

    @Override
    protected MapCodec<ServerRackBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        // A Rack takes any data cable tier — the player picks the face.
        return java.util.EnumSet.allOf(DataTier.class);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BAYS);
    }

    @Override
    public boolean connectsOnFace(final BlockState state, final Direction face) {
        // A Server Rack takes its data cable on the rear only, matching where the rack actually reads
        // the network (the back of its lower-rear block), so the cable never appears to attach elsewhere.
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public java.util.List<BlockPos> footprint(final BlockPos origin, final Direction facing) {
        return ServerRackStructure.allPositions(origin, facing);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction facing = context.getHorizontalDirection().getOpposite();
        if (!canPlaceAt(context.getLevel(), context.getClickedPos(), facing)) {
            return null; // no room for the 2x3x2 cabinet — cancel placement, item not consumed
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state,
                            @Nullable final LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Build the whole cabinet on BOTH sides so the client predicts it at once
        final Direction facing = state.getValue(FACING);
        final boolean server = !level.isClientSide();
        for (final BlockPos part : ServerRackStructure.partPositions(pos, facing)) {
            final boolean top = ServerRackStructure.isTopLayer(pos, part);
            level.setBlock(part, ComputingModule.SERVER_RACK_PART.get().defaultBlockState()
                    .setValue(ServerRackPartBlock.TOP, top)
                    .setValue(ServerRackPartBlock.FACING, facing)
                    .setValue(ServerRackPartBlock.FRONT,
                            ServerRackStructure.isFrontBayBlock(pos, facing, part)),
                    Block.UPDATE_ALL);
            if (server && level.getBlockEntity(part) instanceof ServerRackPartBlockEntity partBe) {
                partBe.setController(pos);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack
                && stack.getItem() instanceof ServerItem) {
            final var servers = rack.getServers();
            for (int i = 0; i < servers.getSlots(); i++) {
                if (servers.getStackInSlot(i).isEmpty()) {
                    servers.setStackInSlot(i, stack.split(1));
                    return ItemInteractionResult.sidedSuccess(false);
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new dev.jsc.jscomputronics.module.computing.menu.ServerRackMenu(id, inv, rack),
                    Component.translatable("block.jsc.server_rack")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        // Drops happen here (not in dissolve) so creative mode never spills items.
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild) {
            dropContents(serverLevel, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
                rack.onBroken(serverLevel); // unregister the housed Server nodes
            }
            dissolve(serverLevel, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static final java.util.Set<BlockPos> DISSOLVING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void dissolve(final ServerLevel level, final BlockPos controllerPos, final Direction facing) {
        if (!DISSOLVING.add(controllerPos.immutable())) {
            return;
        }
        try {
            for (final BlockPos part : ServerRackStructure.partPositions(controllerPos, facing)) {
                if (level.getBlockState(part).getBlock() instanceof ServerRackPartBlock) {
                    level.removeBlock(part, false);
                }
            }
            if (level.getBlockState(controllerPos).getBlock() instanceof ServerRackBlock) {
                level.removeBlock(controllerPos, false);
            }
        } finally {
            DISSOLVING.remove(controllerPos);
        }
    }

    static void dropContents(final ServerLevel level, final BlockPos controllerPos) {
        Block.popResource(level, controllerPos, new ItemStack(ComputingModule.SERVER_RACK_ITEM.get()));
        if (level.getBlockEntity(controllerPos) instanceof ServerRackBlockEntity rack) {
            final var servers = rack.getServers();
            for (int i = 0; i < servers.getSlots(); i++) {
                final ItemStack server = servers.getStackInSlot(i);
                if (!server.isEmpty()) {
                    Block.popResource(level, controllerPos, server);
                    servers.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ServerRackBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ComputingModule.SERVER_RACK_BE.get(),
                ServerRackBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <A extends BlockEntity, E extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            final BlockEntityType<A> given, final BlockEntityType<E> expected,
            final BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
