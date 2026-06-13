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
import dev.jsc.jscomputronics.common.peripheral.PeripheralCableType;
import dev.jsc.jscomputronics.common.peripheral.PeripheralConnectable;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframePartBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.MainframeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A structural part of the Mainframe multiblock — one of the 11 non-controller blocks.
 */
public class MainframePartBlock extends HorizontalDirectionalBlock
        implements EntityBlock, DataNetworkConnectable, PeripheralConnectable {

    public static final MapCodec<MainframePartBlock> CODEC = simpleCodec(MainframePartBlock::new);

    @Override
    public PeripheralCableType peripheralType() {
        // The whole Mainframe footprint is a COMPUTING peripheral owner, so a
        // Peripheral Cable may attach to any part's face, not just the controller.
        return PeripheralCableType.COMPUTING;
    }

    public static final BooleanProperty CORE = BooleanProperty.create("core");

    public MainframePartBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CORE, false));
    }

    @Override
    protected MapCodec<MainframePartBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        // The whole Mainframe footprint takes HBW — a cable may attach to any face.
        return java.util.Set.of(DataTier.T2_HBW);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORE);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MainframePartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof MainframeBlockEntity controller) {
            final BlockPos controllerPos = part.controllerPos();
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new MainframeMenu(id, inventory, controller),
                            Component.translatable("block.jsc.mainframe")),
                    buf -> buf.writeBlockPos(controllerPos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        // Drops happen here (not in dissolve) so creative mode never spills items.
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof MainframePartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockState(part.controllerPos()).getBlock()
                        instanceof dev.jsc.jscomputronics.common.multiblock.AbstractMultiblockControllerBlock controller) {
            controller.dropContentsExternally(serverLevel, part.controllerPos());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MainframePartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockState(part.controllerPos()).getBlock()
                        instanceof dev.jsc.jscomputronics.common.multiblock.AbstractMultiblockControllerBlock controller) {
            controller.dissolve(serverLevel, part.controllerPos(), state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MainframePartBlockEntity(pos, state);
    }
}
