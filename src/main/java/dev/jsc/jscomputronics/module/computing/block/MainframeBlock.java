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
import dev.jsc.jscomputronics.common.multiblock.MultiblockPatternGeometry;
import dev.jsc.jscomputronics.common.util.BlockDrops;
import dev.jsc.jscomputronics.common.util.BlockEntityTickers;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

import java.util.List;

/**
 * The Mainframe — the network's orchestrator.
 */
public class MainframeBlock extends AbstractMultiblockControllerBlock
        implements dev.jsc.jscomputronics.common.network.DataNetworkConnectable,
        dev.jsc.jscomputronics.common.peripheral.PeripheralConnectable {

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
    protected MultiblockGeometry geometry() {
        return new MultiblockPatternGeometry(MainframeStructure.PATTERN);
    }

    @Override
    protected boolean isOwnPart(final BlockState state) {
        return state.getBlock() instanceof MainframePartBlock;
    }

    @Override
    protected boolean isOwnController(final BlockState state) {
        return state.getBlock() instanceof MainframeBlock;
    }

    @Override
    protected BlockState partStateFor(final BlockPos controller, final Direction facing,
                                      final BlockPos part, final BlockState controllerState) {
        return ComputingModule.MAINFRAME_PART.get().defaultBlockState()
                .setValue(FACING, facing)
                .setValue(MainframePartBlock.CORE,
                        MainframeStructure.isCentralColumn(controller, facing, part));
    }

    @Override
    protected void dropContents(final ServerLevel level, final BlockPos controller) {
        Block.popResource(level, controller, new ItemStack(ComputingModule.MAINFRAME_ITEM.get()));
        if (level.getBlockEntity(controller) instanceof MainframeBlockEntity be) {
            BlockDrops.spill(level, controller, be.getInventory());
        }
    }

    @Override
    protected void onControllerBroken(final ServerLevel level, final BlockPos controller) {
        if (level.getBlockEntity(controller) instanceof MainframeBlockEntity be) {
            be.onBroken();
        }
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction facing = context.getHorizontalDirection().getOpposite();
        final Level level = context.getLevel();
        final List<BlockPos> obstructedBlocks = getObstructedBlocks(level, context.getClickedPos(), facing);
        if (!obstructedBlocks.isEmpty()) {
            // No room for the 3x2x2 structure — cancel placement, item not consumed, and outline the
            // obstructing cells with particles so the player can see what is in the way.
            spawnMisplaceParticles(level, obstructedBlocks);
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
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
        return BlockEntityTickers.create(type, ComputingModule.MAINFRAME_BE.get(),
                MainframeBlockEntity::serverTick);
    }

}
