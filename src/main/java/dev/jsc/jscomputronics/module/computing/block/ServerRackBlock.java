/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jsc.jscomputronics.common.multiblock.AbstractMultiblockControllerBlock;
import dev.jsc.jscomputronics.common.multiblock.MultiblockGeometry;
import dev.jsc.jscomputronics.common.network.RearFacingDataPort;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.util.BlockDrops;
import dev.jsc.jscomputronics.common.util.BlockEntityTickers;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
public class ServerRackBlock extends AbstractMultiblockControllerBlock
        implements RearFacingDataPort {

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
    protected MultiblockGeometry geometry() {
        return ServerRackStructure.GEOMETRY;
    }

    @Override
    protected boolean isOwnPart(final BlockState state) {
        return state.getBlock() instanceof ServerRackPartBlock;
    }

    @Override
    protected boolean isOwnController(final BlockState state) {
        return state.getBlock() instanceof ServerRackBlock;
    }

    @Override
    protected BlockState partStateFor(final BlockPos controller, final Direction facing,
                                      final BlockPos part, final BlockState controllerState) {
        return ComputingModule.SERVER_RACK_PART.get().defaultBlockState()
                .setValue(ServerRackPartBlock.TOP, ServerRackStructure.isTopLayer(controller, part))
                .setValue(ServerRackPartBlock.FACING, facing)
                .setValue(ServerRackPartBlock.FRONT,
                        ServerRackStructure.isFrontBayBlock(controller, facing, part));
    }

    @Override
    protected void dropContents(final ServerLevel level, final BlockPos controller) {
        Block.popResource(level, controller, new ItemStack(ComputingModule.SERVER_RACK_ITEM.get()));
        if (level.getBlockEntity(controller) instanceof ServerRackBlockEntity rack) {
            BlockDrops.spill(level, controller, rack.getServers());
        }
    }

    @Override
    protected void onControllerBroken(final ServerLevel level, final BlockPos controller) {
        if (level.getBlockEntity(controller) instanceof ServerRackBlockEntity rack) {
            rack.onBroken(level); // unregister the housed Server nodes
        }
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
        return BlockEntityTickers.create(type, ComputingModule.SERVER_RACK_BE.get(),
                ServerRackBlockEntity::serverTick);
    }

}
