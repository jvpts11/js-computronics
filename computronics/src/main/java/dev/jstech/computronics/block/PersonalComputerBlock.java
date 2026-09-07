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
import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.core.network.DataTier;
import dev.jstech.core.network.IRearFacingDataPort;
import dev.jstech.core.util.BlockDrops;
import dev.jstech.core.util.BlockEntityTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * The Personal Computer: the player's hands-on access point to the network, assembled on a consumer ATX board.
 */
public class PersonalComputerBlock extends HorizontalDirectionalBlock
        implements EntityBlock, IRearFacingDataPort, IEraChassisBlock,
        dev.jstech.core.peripheral.IPeripheralConnectable {

    @Override
    public dev.jstech.core.peripheral.PeripheralCableType peripheralType() {
        return dev.jstech.core.peripheral.PeripheralCableType.COMPUTING;
    }

    @Override
    public dev.jstech.core.tier.HardwareEra chassisEra() {
        return era();
    }

    public static final MapCodec<PersonalComputerBlock> CODEC = simpleCodec(PersonalComputerBlock::new);

    public PersonalComputerBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * The hardware era this Personal Computer belongs to. It selects the block's skin and gates which
     * consumer board the machine accepts: only a board of this era (and of the era's form factor) installs.
     * The base block is the Standard era; the Vintage and Legacy variants override this.
     */
    public dev.jstech.core.tier.HardwareEra era() {
        return dev.jstech.core.tier.HardwareEra.STANDARD;
    }

    @Override
    protected MapCodec<? extends PersonalComputerBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        return java.util.Set.of(DataTier.T1_ETHERNET); // PCs are Ethernet-only; reach HBW via a Personal Router
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
        // The computer block is hardware only: clicking it always opens the hardware-assembly GUI.
        // All software (firmware, OS) is used on a linked monitor, never on the computer block.
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof PersonalComputerBlockEntity computer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new dev.jstech.computronics.menu.PersonalComputerMenu(
                                    id, inventory, computer),
                            getName()),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof PersonalComputerBlockEntity computer) {
            computer.onBroken(serverLevel); // drop this PC's network-node registration
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        // Spill the installed hardware so a broken PC never destroys its components.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof PersonalComputerBlockEntity computer) {
            BlockDrops.spill(serverLevel, pos, computer.getHardware());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new PersonalComputerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return BlockEntityTickers.create(type, ComputingModule.PERSONAL_COMPUTER_BE.get(),
                PersonalComputerBlockEntity::serverTick);
    }

}
