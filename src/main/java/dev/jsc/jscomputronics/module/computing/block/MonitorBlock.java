/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jsc.jscomputronics.common.peripheral.PeripheralCableType;
import dev.jsc.jscomputronics.common.peripheral.PeripheralConnectable;
import dev.jsc.jscomputronics.common.peripheral.PeripheralOwner;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.PeripheralLinks;
import dev.jsc.jscomputronics.module.computing.blockentity.MonitorBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Monitor: a peripheral that displays the interface of the computer it is linked to (over a Peripheral Cable, ≤ 16 blocks).
 */
public class MonitorBlock extends HorizontalDirectionalBlock implements EntityBlock, PeripheralConnectable {

    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public MonitorBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<MonitorBlock> codec() {
        return CODEC;
    }

    @Override
    public PeripheralCableType peripheralType() {
        return PeripheralCableType.COMPUTING;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        // A Monitor is meant to be looked AT, so the screen faces the player who
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(LIT, false);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            final BlockPos owner = monitor.ownerPos();
            if (owner == null) {
                // Explain WHY the screen is dark instead of a generic "not linked", so a missing GPU
                // (the most common cause) or a full host is obvious rather than silent.
                serverPlayer.displayClientMessage(diagnoseUnlinked(level, pos), true);
            } else {
                // The terminal opens; the Command Prompt is reached from its Console rail entry.
                openTerminal(serverPlayer, level, pos, owner);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void openTerminal(final ServerPlayer player, final Level level,
                                     final BlockPos monitorPos, final BlockPos owner) {
        if (level.getBlockEntity(owner) instanceof ComputerTerminalHost host) {
            final Component title = level.getBlockState(owner).getBlock().getName();
            // Reopen on the tab the player last used here (persisted on the Monitor).
            final int initialTab =
                    level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor
                            ? monitor.lastTab() : ComputerTerminalMenu.TAB_NETWORK;
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ComputerTerminalMenu(id, inv, host, owner, monitorPos, initialTab), title),
                    buf -> {
                        buf.writeBlockPos(monitorPos);
                        buf.writeBlockPos(owner);
                        buf.writeVarInt(initialTab);
                    });
        }
    }

    private static Component diagnoseUnlinked(final Level level, final BlockPos monitorPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Component.translatable("block.jsc.monitor.unlinked");
        }
        final java.util.OptionalLong host =
                PeripheralLinks.discoverOwner(serverLevel, monitorPos.asLong());
        if (host.isEmpty()) {
            return Component.translatable("block.jsc.monitor.no_computer");
        }
        if (serverLevel.getBlockEntity(BlockPos.of(host.getAsLong())) instanceof PeripheralOwner owner) {
            if (owner.maxEndpoints() <= 0) {
                return Component.translatable("block.jsc.monitor.no_gpu");
            }
            if (owner.linkedEndpoints().size() >= owner.maxEndpoints()) {
                return Component.translatable("block.jsc.monitor.at_capacity");
            }
        }
        return Component.translatable("block.jsc.monitor.unlinked");
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            monitor.unlink(serverLevel); // free the computer's endpoint slot
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ComputingModule.MONITOR_BE.get(), MonitorBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <A extends BlockEntity, E extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            final BlockEntityType<A> given, final BlockEntityType<E> expected,
            final BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
