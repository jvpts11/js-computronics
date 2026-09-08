/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.core.multiblock.AbstractMultiblockControllerBlock;
import dev.jstech.core.multiblock.IMultiblockGeometry;
import dev.jstech.core.multiblock.MultiblockPatternGeometry;
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
 * The Mainframe, the network's orchestrator.
 */
public class MainframeBlock extends AbstractMultiblockControllerBlock
        implements dev.jstech.core.network.IDataNetworkConnectable,
        dev.jstech.core.peripheral.IPeripheralConnectable, IEraChassisBlock {

    public static final MapCodec<MainframeBlock> CODEC = simpleCodec(MainframeBlock::new);

    @Override
    public dev.jstech.core.peripheral.PeripheralCableType peripheralType() {
        return dev.jstech.core.peripheral.PeripheralCableType.COMPUTING;
    }

    public MainframeBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * The hardware era this Mainframe belongs to. It selects the block's skin and gates which MTX board
     * installs: only a board of this same era is accepted and counted in the build. The base is
     * {@link dev.jstech.core.tier.HardwareEra#STANDARD}; the Vintage and Legacy variants
     * override it.
     */
    public dev.jstech.core.tier.HardwareEra era() {
        return dev.jstech.core.tier.HardwareEra.STANDARD;
    }

    @Override
    public dev.jstech.core.tier.HardwareEra chassisEra() {
        return era();
    }

    /**
     * The item this Mainframe drops and is picked as: its own era variant. Overridden per era so a
     * broken or pick-blocked Mainframe yields the matching era's item.
     */
    protected net.minecraft.world.item.Item blockItem() {
        return ComputingModule.MAINFRAME_ITEM.get();
    }

    @Override
    protected MapCodec<? extends MainframeBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<dev.jstech.core.network.DataTier> acceptedCableTiers() {
        /*
         * The Mainframe sits on the HBW backbone; it never takes an Ethernet
         * access link directly (a Personal Router bridges that).
         */
        return java.util.Set.of(dev.jstech.core.network.DataTier.T2_HBW);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected IMultiblockGeometry geometry() {
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
        // Stamp the controller's era onto every structural part so the whole footprint wears one skin.
        final dev.jstech.core.tier.HardwareEra era =
                controllerState.getBlock() instanceof MainframeBlock mf
                        ? mf.era()
                        : dev.jstech.core.tier.HardwareEra.STANDARD;
        return ComputingModule.MAINFRAME_PART.get().defaultBlockState()
                .setValue(FACING, facing)
                .setValue(MainframePartBlock.CORE,
                        MainframeStructure.isCentralColumn(controller, facing, part))
                .setValue(MainframePartBlock.ERA, era.level());
    }

    @Override
    protected void dropContents(final ServerLevel level, final BlockPos controller) {
        Block.popResource(level, controller, new ItemStack(blockItem()));
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
            /*
             * No room for the 3x2x2 structure, so cancel placement, item not consumed, and outline the
             * obstructing cells with particles so the player can see what is in the way.
             */
            spawnMisplaceParticles(level, obstructedBlocks);
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        /*
         * The Mainframe block is hardware only: clicking it always opens the hardware-assembly GUI
         * (the same one its parts open). All software (firmware, OS) is used on a linked monitor.
         */
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe) {
            /*
             * Sneaking takes the service panel off the card bay (or puts it back): the processors,
             * memory and cards are only ever seen through that opening.
             */
            if (player.isShiftKeyDown()) {
                mainframe.toggleServicePanel();
                return InteractionResult.sidedSuccess(false);
            }
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new dev.jstech.computers.menu.MainframeMenu(
                                    id, inventory, mainframe),
                            // Each era is its own machine and carries its own name in the GUI header.
                            state.getBlock().getName()),
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
