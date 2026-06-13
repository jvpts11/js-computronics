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
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackPartBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A structural part of the Server Rack — one of the 11 non-controller blocks of the 2x3x2 cabinet.
 */
public class ServerRackPartBlock extends Block implements EntityBlock, RearFacingDataPort {

    public static final MapCodec<ServerRackPartBlock> CODEC = simpleCodec(ServerRackPartBlock::new);

    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public static final BooleanProperty FRONT = BooleanProperty.create("front");

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public ServerRackPartBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(TOP, false)
                .setValue(FRONT, false)
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(ServerRackBlock.BAYS, 0));
    }

    @Override
    protected MapCodec<ServerRackPartBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        return java.util.EnumSet.allOf(DataTier.class);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, FRONT, FACING, ServerRackBlock.BAYS);
    }

    @Nullable
    private static ServerRackBlockEntity controllerOf(final Level level, final BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof ServerRackBlockEntity rack) {
            return rack;
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        final ServerRackBlockEntity rack = controllerOf(level, pos);
        if (rack != null && stack.getItem() instanceof ServerItem) {
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
                && level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof ServerRackBlockEntity rack) {
            final BlockPos controllerPos = part.controllerPos();
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new dev.jsc.jscomputronics.module.computing.menu.ServerRackMenu(id, inv, rack),
                    Component.translatable("block.jsc.server_rack")),
                    buf -> buf.writeBlockPos(controllerPos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        // Drops happen here (not in dissolve) so creative mode never spills items.
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null) {
            ServerRackBlock.dropContents(serverLevel, part.controllerPos());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null) {
            // The controller is still present when a part is broken; read its facing
            // so the whole cabinet dissolves. (Re-entrant calls are guarded.)
            final BlockState controller = level.getBlockState(part.controllerPos());
            if (controller.getBlock() instanceof ServerRackBlock) {
                ServerRackBlock.dissolve(serverLevel, part.controllerPos(),
                        controller.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ServerRackPartBlockEntity(pos, state);
    }
}
