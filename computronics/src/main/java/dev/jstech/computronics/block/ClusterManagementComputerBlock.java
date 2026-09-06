/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity;
import dev.jstech.core.network.DataTier;
import dev.jstech.core.network.RearFacingDataPort;
import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.peripheral.PeripheralConnectable;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.core.util.BlockDrops;
import dev.jstech.core.util.BlockEntityTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
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

import java.util.Set;

/**
 * The Cluster Management Computer: a full computer whose job is the racks. It sits on the data network
 * like any other machine and, with a Cluster Interface Card installed, drives every supercomputer
 * fabric and datacenter section its network reaches — installing systems and programs on all their
 * nodes, switching bays, watching queues. Without the card it is an ordinary computer. A cluster
 * works without one; the computer makes it one machine to run.
 */
public class ClusterManagementComputerBlock extends HorizontalDirectionalBlock
        implements EntityBlock, RearFacingDataPort, PeripheralConnectable, EraChassisBlock {

    public static final MapCodec<ClusterManagementComputerBlock> CODEC = simpleCodec(ClusterManagementComputerBlock::new);

    public ClusterManagementComputerBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public HardwareEra era() {
        return HardwareEra.STANDARD;
    }

    @Override
    public HardwareEra chassisEra() {
        return era();
    }

    @Override
    protected MapCodec<? extends ClusterManagementComputerBlock> codec() {
        return CODEC;
    }

    @Override
    public PeripheralCableType peripheralType() {
        return PeripheralCableType.COMPUTING;
    }

    @Override
    public Set<DataTier> acceptedCableTiers() {
        // A management machine lives on the backbone: Ethernet through a router, or the bandwidth
        // and fibre cables directly. Never the compute fabric — the racks are reached over the network.
        return Set.of(DataTier.T1_ETHERNET, DataTier.T2_HBW, DataTier.T3_FIBER);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ClusterManagementComputerBlockEntity computer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new dev.jstech.computronics.menu
                            .ClusterManagementComputerMenu(id, inventory, computer),
                    Component.translatable("block.jsc.cluster_management_computer")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ClusterManagementComputerBlockEntity computer) {
            computer.onBroken(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof ClusterManagementComputerBlockEntity computer) {
            BlockDrops.spill(serverLevel, pos, computer.getHardware());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ClusterManagementComputerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state,
                                                                  final BlockEntityType<T> type) {
        return BlockEntityTickers.create(type, ComputingModule.CLUSTER_MANAGEMENT_COMPUTER_BE.get(),
                ClusterManagementComputerBlockEntity::serverTick);
    }
}
