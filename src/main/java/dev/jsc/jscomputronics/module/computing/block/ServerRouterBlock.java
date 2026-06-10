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
import dev.jsc.jscomputronics.common.network.NetworkBridge;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRouterBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.ServerRouterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Server Router: a network topology element that switches the network and groups Server Racks into datacenter sections, one per output face.
 */
public class ServerRouterBlock extends Block
        implements EntityBlock, DataNetworkConnectable, NetworkBridge {

    public static final MapCodec<ServerRouterBlock> CODEC = simpleCodec(ServerRouterBlock::new);

    public ServerRouterBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ServerRouterBlock> codec() {
        return CODEC;
    }

    // acceptedCableTiers() defaults to every tier: the router input takes any cable family.

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player,
                                               final net.minecraft.world.phys.BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ServerRouterBlockEntity router)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            router.recomputeNow(); // open with a fresh topology summary
            serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new ServerRouterMenu(id, inv, router, router.customName()),
                            state.getBlock().getName()),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeUtf(router.customName());
                    });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(pos) instanceof ServerRouterBlockEntity router) {
                router.onBroken(serverLevel);
            }
            NetworkSystem.get(serverLevel).connectivity().onCableRemoved(pos.asLong());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ServerRouterBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ComputingModule.SERVER_ROUTER_BE.get(),
                ServerRouterBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <A extends BlockEntity, E extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            final BlockEntityType<A> given, final BlockEntityType<E> expected,
            final BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
