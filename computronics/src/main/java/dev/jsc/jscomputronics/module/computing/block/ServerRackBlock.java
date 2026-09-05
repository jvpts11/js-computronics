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
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import dev.jsc.jscomputronics.module.computing.rack.RackChassis;
import dev.jsc.jscomputronics.module.computing.rack.MountableRackUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Server Rack: a 2-wide, 3-tall, 2-deep multiblock cabinet that is logically a single rack.
 */
public class ServerRackBlock extends AbstractMultiblockControllerBlock
        implements RearFacingDataPort {

    public static final MapCodec<ServerRackBlock> CODEC = simpleCodec(ServerRackBlock::new);

    public static final net.minecraft.world.level.block.state.properties.IntegerProperty BAYS =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("bays", 0, 3);

    public ServerRackBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BAYS, 0));
    }

    @Override
    protected MapCodec<? extends ServerRackBlock> codec() {
        // Wildcard so a typed cabinet (the Supercomputer Rack) can answer with its own codec.
        return CODEC;
    }

    /**
     * The kind of cabinet this block is, which decides the computers it seats. The block entity reads
     * it from here, so the one cabinet code path serves every rack type.
     */
    public dev.jsc.jscomputronics.module.computing.rack.RackChassis.RackType rackType() {
        return dev.jsc.jscomputronics.module.computing.rack.RackChassis.RackType.SERVER;
    }

    /**
     * The era of the cabinet. A cabinet seats servers of its own era or earlier, so a Standard rack
     * takes everything and a Vintage rack takes only Vintage servers.
     */
    public dev.jsc.jscomputronics.common.tier.HardwareEra era() {
        return dev.jsc.jscomputronics.common.tier.HardwareEra.STANDARD;
    }

    /**
     * The whole cabinet is one model drawn by the controller's block-entity renderer; the block itself
     * shows nothing, so the eleven part blocks and this one never paint a seam over it.
     */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(final BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /**
     * The item this cabinet drops when torn down and is picked as. A typed cabinet answers with its own,
     * so dismantling a Supercomputer Rack never hands the player a Server Rack.
     */
    protected net.minecraft.world.item.Item blockItem() {
        return ComputingModule.SERVER_RACK_ITEM.get();
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        // A Server Rack takes any data cable tier but the high-compute fabric, which belongs to the
        // supercomputer cabinet alone.
        return java.util.EnumSet.complementOf(java.util.EnumSet.of(DataTier.HPC));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BAYS);
    }

    @Override
    protected MultiblockGeometry geometry() {
        return new MultiblockPatternGeometry(ServerRackStructure.PATTERN);
    }

    @Override
    protected boolean isOwnPart(final BlockState state) {
        return state.getBlock() instanceof ServerRackPartBlock;
    }

    @Override
    protected boolean isOwnController(final BlockState state) {
        return state.getBlock() instanceof ServerRackBlock;
    }

    @Override
    protected BlockState partStateFor(final BlockPos controller, final Direction facing,
                                      final BlockPos part, final BlockState controllerState) {
        return ComputingModule.SERVER_RACK_PART.get().defaultBlockState()
                .setValue(ServerRackPartBlock.TOP, ServerRackStructure.isTopLayer(controller, part))
                .setValue(ServerRackPartBlock.FACING, facing)
                .setValue(ServerRackPartBlock.COMPUTE,
                        rackType() == dev.jsc.jscomputronics.module.computing.rack.RackChassis.RackType.SUPERCOMPUTER)
                .setValue(ServerRackPartBlock.FRONT,
                        ServerRackStructure.isFrontBayBlock(controller, facing, part));
    }

    @Override
    protected void dropContents(final ServerLevel level, final BlockPos controller) {
        Block.popResource(level, controller, new ItemStack(blockItem()));
        if (level.getBlockEntity(controller) instanceof ServerRackBlockEntity rack) {
            BlockDrops.spill(level, controller, rack.getServers());
            // The hotswap drives belong to the rack, not to the servers, so they spill too.
            BlockDrops.spill(level, controller, rack.getFrontSlots());
        }
    }

    @Override
    protected void onControllerBroken(final ServerLevel level, final BlockPos controller) {
        if (level.getBlockEntity(controller) instanceof ServerRackBlockEntity rack) {
            rack.onBroken(level); // unregister the housed Server nodes
        }
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction facing = context.getHorizontalDirection().getOpposite();
        final Level level = context.getLevel();
        final List<BlockPos> obstructedBlocks = getObstructedBlocks(level, context.getClickedPos(), facing);
        if (!obstructedBlocks.isEmpty()) {
            // No room for the 2x3x2 cabinet — cancel placement, item not consumed, and outline the
            // obstructing cells with particles so the player can see what is in the way.
            spawnMisplaceParticles(level, obstructedBlocks);
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            return mountFromHand(rack, pos, pos, stack, player, hit);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // The cabinet model's rack units: rows fill texels 4..92 of the 96 (three blocks of 32) from the
    // top down, 11 texels each, the same layout the rack renderer draws.
    private static final double ROW_BASE_TEXELS = 4.0;
    private static final double ROW_TEXELS = 11.0;
    private static final double TEXELS_PER_BLOCK = 32.0;

    /**
     * Seats a server or rack unit held in the hand. Aimed at the cabinet's front, it goes into the rack
     * row under the crosshair — the row the player is looking at, counted from the top like the rack's
     * slots — and is refused with the reason when that row cannot take it. Aimed anywhere else, it
     * takes the first row that fits, top down. Shared by the controller and the cabinet's part blocks.
     */
    static ItemInteractionResult mountFromHand(final ServerRackBlockEntity rack, final BlockPos controllerPos,
                                               final BlockPos hitPos, final ItemStack stack, final Player player,
                                               final BlockHitResult hit) {
        if (MountableRackUnit.of(stack) == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // A chassis that belongs in another cabinet is refused with the reason, not ignored.
        if (!rack.acceptsChassis(stack)) {
            tell(player, "block.jsc.rack.wrong_chassis");
            return ItemInteractionResult.sidedSuccess(false);
        }
        final ItemStackHandler servers = rack.getServers();
        final int aimed = aimedRow(rack, controllerPos, hitPos, hit);
        if (aimed >= 0) {
            // isItemValid enforces the rack-unit fit: a row covered by a taller chassis is empty but
            // not placeable, and a chassis must fit below its top row too.
            if (servers.getStackInSlot(aimed).isEmpty() && servers.isItemValid(aimed, stack)) {
                servers.setStackInSlot(aimed, stack.split(1));
            } else {
                tell(player, "block.jsc.rack.row_taken");
            }
            return ItemInteractionResult.sidedSuccess(false);
        }
        for (int i = 0; i < servers.getSlots(); i++) {
            if (servers.getStackInSlot(i).isEmpty() && servers.isItemValid(i, stack)) {
                servers.setStackInSlot(i, stack.split(1));
                return ItemInteractionResult.sidedSuccess(false);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** The rack row under the crosshair when the hit lands on the cabinet's front face, else -1. */
    private static int aimedRow(final ServerRackBlockEntity rack, final BlockPos controllerPos,
                                final BlockPos hitPos, final BlockHitResult hit) {
        final BlockState state = rack.getBlockState();
        if (!state.hasProperty(FACING)) {
            return -1;
        }
        final Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) {
            return -1;
        }
        // Only the front layer's blocks show the units: the controller's column and the one clockwise of it.
        final BlockPos flat = hitPos.atY(controllerPos.getY());
        if (!flat.equals(controllerPos) && !flat.equals(controllerPos.relative(facing.getClockWise()))) {
            return -1;
        }
        final double texel = (hit.getLocation().y - controllerPos.getY()) * TEXELS_PER_BLOCK;
        final int fromBottom = (int) Math.floor((texel - ROW_BASE_TEXELS) / ROW_TEXELS);
        return Mth.clamp(ServerRackBlockEntity.CAPACITY_U - 1 - fromBottom, 0, ServerRackBlockEntity.CAPACITY_U - 1);
    }

    /**
     * What the middle mouse button picks off a cabinet: the machine under the crosshair when there is one,
     * otherwise the cabinet itself. Every block of the cabinet answers this (the part blocks delegate here),
     * because a player aiming anywhere at a rack expects to pick the rack — the parts have no item of their
     * own, so picking them used to hand back nothing at all.
     */
    static ItemStack pickFrom(final ServerRackBlockEntity rack, final BlockPos controllerPos,
                              final BlockPos hitPos, final HitResult target, final Item cabinetItem) {
        if (target instanceof BlockHitResult hit) {
            final int row = aimedRow(rack, controllerPos, hitPos, hit);
            if (row >= 0) {
                final ItemStack seated = rack.getServers().getStackInSlot(unitTopRow(rack, row));
                if (!seated.isEmpty()) {
                    return seated.copy();
                }
            }
        }
        return new ItemStack(cabinetItem);
    }

    /** The row holding the machine that occupies {@code row} — a 2U chassis is picked from either of its rows. */
    private static int unitTopRow(final ServerRackBlockEntity rack, final int row) {
        for (int i = 0; i < row; i++) {
            final RackChassis chassis = ServerItem.chassisOf(rack.getServers().getStackInSlot(i));
            if (chassis != null && row < i + chassis.heightU()) {
                return i;
            }
        }
        return row;
    }

    @Override
    public ItemStack getCloneItemStack(final BlockState state, final HitResult target, final LevelReader level,
                                       final BlockPos pos, final Player player) {
        return level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack
                ? pickFrom(rack, pos, pos, target, blockItem())
                : super.getCloneItemStack(state, target, level, pos, player);
    }

    private static void tell(final Player player, final String key) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable(key), true);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            // Sneaking on a supercomputer cabinet takes its livery panel off (or puts it back): the
            // nodes are only ever seen through that opening.
            if (player.isShiftKeyDown()
                    && rack.rackType() == dev.jsc.jscomputronics.module.computing.rack.RackChassis.RackType.SUPERCOMPUTER) {
                rack.toggleServicePanel();
                return InteractionResult.sidedSuccess(false);
            }
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new dev.jsc.jscomputronics.module.computing.menu.ServerRackMenu(id, inv, rack),
                    // The cabinet's own name: a compute cabinet is not a Server Rack, and each era has its
                    // own. It used to open every one of them titled "Server Rack".
                    state.getBlock().getName()),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ServerRackBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return BlockEntityTickers.create(type, ComputingModule.SERVER_RACK_BE.get(),
                ServerRackBlockEntity::serverTick);
    }

}
