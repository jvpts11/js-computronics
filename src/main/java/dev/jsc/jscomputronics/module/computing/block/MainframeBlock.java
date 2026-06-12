/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframePartBlockEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Mainframe — the network's orchestrator.
 */
public class MainframeBlock extends HorizontalDirectionalBlock
        implements EntityBlock, dev.jsc.jscomputronics.common.network.DataNetworkConnectable,
        dev.jsc.jscomputronics.common.peripheral.PeripheralConnectable,
        dev.jsc.jscomputronics.common.multiblock.MultiblockBlock {

    public static final MapCodec<MainframeBlock> CODEC = simpleCodec(MainframeBlock::new);

    @Override
    public dev.jsc.jscomputronics.common.peripheral.PeripheralCableType peripheralType() {
        return dev.jsc.jscomputronics.common.peripheral.PeripheralCableType.COMPUTING;
    }

    public MainframeBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<MainframeBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<dev.jsc.jscomputronics.common.network.DataTier> acceptedCableTiers() {
        // The Mainframe sits on the HBW backbone; it never takes an Ethernet
        // access link directly (a Personal Router bridges that).
        return java.util.Set.of(dev.jsc.jscomputronics.common.network.DataTier.T2_HBW);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public java.util.List<BlockPos> footprint(final BlockPos origin, final Direction facing) {
        return MainframeStructure.allPositions(origin, facing);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction facing = context.getHorizontalDirection().getOpposite();
        if (!canPlaceAt(context.getLevel(), context.getClickedPos(), facing)) {
            return null; // no room for the 3x2x2 structure — cancel placement, item not consumed
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state,
                            @Nullable final LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Build the whole footprint on BOTH sides. The client predicts block
        final Direction facing = state.getValue(FACING);
        final boolean server = !level.isClientSide();
        for (final BlockPos part : MainframeStructure.partPositions(pos, facing)) {
            final boolean core = MainframeStructure.isCentralColumn(pos, facing, part);
            level.setBlock(part, ComputingModule.MAINFRAME_PART.get().defaultBlockState()
                    .setValue(FACING, facing)
                    .setValue(MainframePartBlock.CORE, core), Block.UPDATE_ALL);
            if (server && level.getBlockEntity(part) instanceof MainframePartBlockEntity partBe) {
                partBe.setController(pos);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new dev.jsc.jscomputronics.module.computing.menu.MainframeMenu(
                                    id, inventory, mainframe),
                            Component.translatable("block.jsc.mainframe")),
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
            if (level.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe) {
                mainframe.onBroken();
            }
            dissolve(serverLevel, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static final java.util.Set<BlockPos> DISSOLVING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void dissolve(final ServerLevel level, final BlockPos controllerPos, final Direction facing) {
        if (!DISSOLVING.add(controllerPos.immutable())) {
            return;
        }
        try {
            for (final BlockPos part : MainframeStructure.partPositions(controllerPos, facing)) {
                if (level.getBlockState(part).getBlock() instanceof MainframePartBlock) {
                    level.removeBlock(part, false);
                }
            }
            if (level.getBlockState(controllerPos).getBlock() instanceof MainframeBlock) {
                level.removeBlock(controllerPos, false);
            }
        } finally {
            DISSOLVING.remove(controllerPos);
        }
    }

    static void dropContents(final ServerLevel level, final BlockPos controllerPos) {
        Block.popResource(level, controllerPos, new ItemStack(ComputingModule.MAINFRAME_ITEM.get()));
        if (level.getBlockEntity(controllerPos) instanceof MainframeBlockEntity be) {
            final var inventory = be.getInventory();
            for (int i = 0; i < inventory.getSlots(); i++) {
                final ItemStack part = inventory.getStackInSlot(i);
                if (!part.isEmpty()) {
                    Block.popResource(level, controllerPos, part);
                    inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MainframeBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ComputingModule.MAINFRAME_BE.get(),
                MainframeBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <A extends BlockEntity, E extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            final BlockEntityType<A> given, final BlockEntityType<E> expected,
            final BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
