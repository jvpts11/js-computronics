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
import dev.jsc.jscomputronics.common.network.RearFacingDataPort;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.util.BlockDrops;
import dev.jsc.jscomputronics.common.util.BlockEntityTickers;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Supercomputer Node cabinet controller. The cabinet shares the Server Rack footprint
 * (2 wide, 3 tall, 2 deep) so the two stand side by side in a datacenter aisle.
 */
public class SupercomputerNodeBlock extends AbstractMultiblockControllerBlock
        implements RearFacingDataPort {

    public static final MapCodec<SupercomputerNodeBlock> CODEC = simpleCodec(SupercomputerNodeBlock::new);

    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public SupercomputerNodeBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FILLED, false));
    }

    @Override
    protected MapCodec<SupercomputerNodeBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        return java.util.Set.of(DataTier.HPC); // the cluster fabric is HPC-only
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILLED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction facing = context.getHorizontalDirection().getOpposite();
        final Level level = context.getLevel();
        final List<BlockPos> obstructedBlocks = getObstructedBlocks(level, context.getClickedPos(), facing);
        if (!obstructedBlocks.isEmpty()) {
            // No room for the cabinet — cancel placement, item not consumed, and outline the obstructing
            // cells with particles so the player can see what is in the way.
            spawnMisplaceParticles(level, obstructedBlocks);
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected MultiblockGeometry geometry() {
        return new MultiblockPatternGeometry(ServerRackStructure.PATTERN);
    }

    @Override
    protected boolean isOwnPart(final BlockState state) {
        return state.getBlock() instanceof SupercomputerNodePartBlock;
    }

    @Override
    protected boolean isOwnController(final BlockState state) {
        return state.getBlock() instanceof SupercomputerNodeBlock;
    }

    @Override
    protected BlockState partStateFor(final BlockPos controller, final Direction facing,
                                      final BlockPos part, final BlockState controllerState) {
        return ComputingModule.SUPERCOMPUTER_NODE_PART.get().defaultBlockState()
                .setValue(SupercomputerNodePartBlock.TOP, ServerRackStructure.isTopLayer(controller, part))
                .setValue(SupercomputerNodePartBlock.FRONT,
                        ServerRackStructure.isFrontBayBlock(controller, facing, part))
                .setValue(SupercomputerNodePartBlock.FACING, facing);
    }

    @Override
    protected void dropContents(final ServerLevel level, final BlockPos controller) {
        Block.popResource(level, controller, new ItemStack(ComputingModule.SUPERCOMPUTER_NODE_ITEM.get()));
        if (level.getBlockEntity(controller) instanceof SupercomputerNodeBlockEntity node) {
            BlockDrops.spill(level, controller, node.getHardware());
        }
    }

    @Override
    protected void onControllerBroken(final ServerLevel level, final BlockPos controller) {
        if (level.getBlockEntity(controller) instanceof SupercomputerNodeBlockEntity node) {
            node.onBroken(level);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SupercomputerNodeBlockEntity node) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new dev.jsc.jscomputronics.module.computing.menu
                                    .SupercomputerNodeMenu(id, inventory, node),
                            Component.translatable("block.jsc.supercomputer_node")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new SupercomputerNodeBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return BlockEntityTickers.create(type, ComputingModule.SUPERCOMPUTER_NODE_BE.get(),
                SupercomputerNodeBlockEntity::serverTick);
    }

}
