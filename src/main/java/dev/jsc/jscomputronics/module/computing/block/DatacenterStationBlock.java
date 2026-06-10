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
import dev.jsc.jscomputronics.module.computing.blockentity.DatacenterStationBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.DatacenterStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Datacenter Station: a T3 kiosk that opens an interactive terminal over ONE datacenter section (one Server Router output face), aggregating that section's Servers as a single unit.
 */
public class DatacenterStationBlock extends Block implements EntityBlock, DataNetworkConnectable {

    public static final MapCodec<DatacenterStationBlock> CODEC = simpleCodec(DatacenterStationBlock::new);

    public DatacenterStationBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DatacenterStationBlock> codec() {
        return CODEC;
    }

    // acceptedCableTiers() defaults to every tier: the Station reads whatever cable it sits on.

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DatacenterStationBlockEntity station)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            station.ensureBound(); // pick the first section if none is bound yet
            serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new DatacenterStationMenu(id, inv, station),
                            state.getBlock().getName()),
                    buf -> buf.writeBlockPos(pos));
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads
                        .sendDatacenterSnapshot(serverPlayer, serverLevel, station);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new DatacenterStationBlockEntity(pos, state);
    }
}
