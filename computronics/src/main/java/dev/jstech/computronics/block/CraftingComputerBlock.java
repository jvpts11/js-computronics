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
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.core.network.DataTier;
import dev.jstech.core.network.IRearFacingDataPort;
import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.peripheral.IPeripheralConnectable;
import dev.jstech.core.util.BlockDrops;
import dev.jstech.core.util.BlockEntityTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.jetbrains.annotations.Nullable;

/**
 * The Crafting Computer block: an ATX-class computer that executes recipes for the network.
 */
public class CraftingComputerBlock extends HorizontalDirectionalBlock
        implements EntityBlock, IRearFacingDataPort, IPeripheralConnectable, IEraChassisBlock {

    public static final MapCodec<CraftingComputerBlock> CODEC = simpleCodec(CraftingComputerBlock::new);

    public CraftingComputerBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * The hardware era this Crafting Computer belongs to. It selects the block's skin and gates which
     * board the machine accepts: only a board of this era (and of the era's form factor) installs. The
     * base block is the Standard era; the Vintage and Legacy variants override this.
     */
    public dev.jstech.core.tier.HardwareEra era() {
        return dev.jstech.core.tier.HardwareEra.STANDARD;
    }

    @Override
    public dev.jstech.core.tier.HardwareEra chassisEra() {
        return era();
    }

    @Override
    protected MapCodec<? extends CraftingComputerBlock> codec() {
        return CODEC;
    }

    @Override
    public PeripheralCableType peripheralType() {
        return PeripheralCableType.COMPUTING;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        /*
         * Data via Ethernet (rear port, through a Personal Router to the backbone) plus the crafting cable
         * that runs to the Crafting Switches.
         */
        return java.util.Set.of(DataTier.T1_ETHERNET, DataTier.CRAFTING);
    }

    @Override
    public boolean connectsOnFace(final net.minecraft.world.level.block.state.BlockState state,
                                  final net.minecraft.core.Direction face, final DataTier tier) {
        /*
         * The crafting cable attaches on any face (the machine-delivery search walks out of all six);
         * the data cable keeps the rear-only port.
         */
        return tier == DataTier.CRAFTING || connectsOnFace(state, face);
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
    protected net.minecraft.world.InteractionResult useWithoutItem(
            final BlockState state, final Level level, final BlockPos pos,
            final net.minecraft.world.entity.player.Player player,
            final net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity computer) {
            serverPlayer.openMenu(
                    new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new dev.jstech.computronics.menu.CraftingComputerMenu(
                                    id, inventory, computer),
                            net.minecraft.network.chat.Component.translatable("block.jsc.crafting_computer")),
                    buf -> buf.writeBlockPos(pos));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity computer) {
            computer.onBroken(serverLevel); // drop this computer's network-node registration
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final net.minecraft.world.entity.player.Player player) {
        // Spill the installed hardware so a broken Crafting Computer never destroys its components.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity computer) {
            BlockDrops.spill(serverLevel, pos, computer.getHardware());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new CraftingComputerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return BlockEntityTickers.create(type, ComputingModule.CRAFTING_COMPUTER_BE.get(),
                CraftingComputerBlockEntity::serverTick);
    }

}
