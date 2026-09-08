/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.computers.blockentity.ServerRackBlockEntity;
import dev.jstech.computers.blockentity.ServerRackPartBlockEntity;
import dev.jstech.core.network.DataTier;
import dev.jstech.core.network.IRearFacingDataPort;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A structural part of the Server Rack, one of the 11 non-controller blocks of the 2x3x2 cabinet.
 */
public class ServerRackPartBlock extends Block implements EntityBlock, IRearFacingDataPort {

    public static final MapCodec<ServerRackPartBlock> CODEC = simpleCodec(ServerRackPartBlock::new);

    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public static final BooleanProperty FRONT = BooleanProperty.create("front");
    /**
     * Whether this part belongs to a Supercomputer Rack. The parts are one shared block, and a model
     * cannot ask the controller what cabinet it is, so the controller stamps the cabinet type on each
     * part it raises, and that is what lets the whole cabinet wear one livery, not just its base block.
     */
    public static final BooleanProperty COMPUTE = BooleanProperty.create("compute");

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public ServerRackPartBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(TOP, false)
                .setValue(FRONT, false)
                .setValue(COMPUTE, false)
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(ServerRackBlock.BAYS, 0));
    }

    /** A part is collision and a link back to the controller; the cabinet model is drawn from there. */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(final BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }

    @Override
    protected MapCodec<ServerRackPartBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        return java.util.EnumSet.allOf(DataTier.class);
    }

    @Override
    public boolean connectsOnFace(final BlockState state, final net.minecraft.core.Direction face,
                                  final DataTier tier) {
        /*
         * A compute cabinet is on the high-compute fabric only; a server cabinet takes every data tier but
         * that one. The cable's rendered nub and the cabinet's own link follow this same rule, so a data
         * cable on a supercomputer cabinet neither shows a connection nor makes one.
         */
        return state.getValue(COMPUTE) == (tier == DataTier.HPC);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, FRONT, COMPUTE, FACING, ServerRackBlock.BAYS);
    }

    @Nullable
    private static ServerRackBlockEntity controllerOf(final Level level, final BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof ServerRackBlockEntity rack) {
            return rack;
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof ServerRackBlockEntity rack) {
            // The part knows its cabinet; the row under the crosshair is resolved against the controller.
            return ServerRackBlock.mountFromHand(rack, part.controllerPos(), pos, stack, player, hit);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof ServerRackBlockEntity rack) {
            final BlockPos controllerPos = part.controllerPos();
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new dev.jstech.computers.menu.ServerRackMenu(id, inv, rack),
                    // The cabinet names itself; every era and the compute cabinet have their own.
                    rack.getBlockState().getBlock().getName()),
                    buf -> buf.writeBlockPos(controllerPos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * A part has no item of its own, so the middle mouse button used to pick nothing at all from eleven of
     * the cabinet's twelve blocks. It answers with what the cabinet would: the machine under the crosshair,
     * or the cabinet itself.
     */
    @Override
    public ItemStack getCloneItemStack(final BlockState state, final net.minecraft.world.phys.HitResult target,
                                       final net.minecraft.world.level.LevelReader level, final BlockPos pos,
                                       final Player player) {
        if (level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof ServerRackBlockEntity rack
                && rack.getBlockState().getBlock() instanceof ServerRackBlock cabinet) {
            return ServerRackBlock.pickFrom(rack, part.controllerPos(), pos, target, cabinet.blockItem());
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        // Drops happen here (not in dissolve) so creative mode never spills items.
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockState(part.controllerPos()).getBlock()
                        instanceof dev.jstech.core.multiblock.AbstractMultiblockControllerBlock controller) {
            controller.dropContentsExternally(serverLevel, part.controllerPos());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ServerRackPartBlockEntity part
                && part.controllerPos() != null) {
            /*
             * The controller is still present when a part is broken; read its facing
             * so the whole cabinet dissolves. (Re-entrant calls are guarded.)
             */
            final BlockState controller = level.getBlockState(part.controllerPos());
            if (controller.getBlock()
                    instanceof dev.jstech.core.multiblock.AbstractMultiblockControllerBlock owner) {
                owner.dissolve(serverLevel, part.controllerPos(),
                        controller.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ServerRackPartBlockEntity(pos, state);
    }
}
