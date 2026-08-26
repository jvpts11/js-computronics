/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.media;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jsc.jscomputronics.common.peripheral.PeripheralCableType;
import dev.jsc.jscomputronics.common.peripheral.PeripheralConnectable;
import dev.jsc.jscomputronics.common.util.BlockEntityTickers;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.Containers;
import org.jetbrains.annotations.Nullable;

/**
 * A media reader peripheral: a slot that holds one {@link MediaItem} whose {@link MediaFormat} this
 * drive accepts. Right-click inserts the held media; sneak-right-click ejects it. The loaded payload
 * is available via {@link MediaReaderBlockEntity#insertedPayload()}.
 *
 * <p>The concrete drive (Floppy / CD / DVD / Dock) is decided by the {@link MediaDriveType} passed at
 * registration, which also controls which media formats the slot accepts.
 *
 * <p>Participates in the {@link PeripheralCableType#COMPUTING} peripheral system as an endpoint, so a
 * reader placed within 16 cable blocks of a computer auto-discovers and links to that computer. The
 * computer can then query all linked readers to locate OS installation media without requiring the
 * reader to be physically adjacent.
 */
public class MediaReaderBlock extends HorizontalDirectionalBlock implements EntityBlock, PeripheralConnectable {

    public static final MapCodec<MediaReaderBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.xmap(MediaDriveType::valueOf, MediaDriveType::name)
                            .fieldOf("drive_type").forGetter(b -> b.driveType),
                    propertiesCodec()
            ).apply(instance, MediaReaderBlock::new));

    /** True when the slot holds a medium, so the model shows the "loaded" (lit LED) front face. */
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");

    private final MediaDriveType driveType;

    public MediaReaderBlock(final MediaDriveType driveType, final Properties properties) {
        super(properties);
        this.driveType = driveType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOADED, false));
    }

    /** The kind of drive this block is, deciding which media formats its slot accepts. */
    public MediaDriveType driveType() {
        return driveType;
    }

    @Override
    protected MapCodec<MediaReaderBlock> codec() {
        return CODEC;
    }

    @Override
    public PeripheralCableType peripheralType() {
        return PeripheralCableType.COMPUTING;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOADED);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack heldStack, final BlockState state,
                                              final Level level, final BlockPos pos,
                                              final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof MediaReaderBlockEntity reader)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player.isShiftKeyDown()) {
            // Sneak-click: eject any loaded media back to the player.
            final ItemStack ejected = reader.ejectMedia();
            if (!ejected.isEmpty()) {
                if (!player.addItem(ejected)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, ejected);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        // Normal click: insert held media if the slot is empty and the drive accepts its format.
        if (reader.acceptsMedia(heldStack)) {
            final ItemStack inserted = reader.insertMedia(heldStack.copyWithCount(1));
            if (inserted.isEmpty()) {
                // Insertion succeeded: shrink the player's stack by 1.
                heldStack.shrink(1);
                return ItemInteractionResult.SUCCESS;
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof MediaReaderBlockEntity reader) {
            reader.dropContents(level, pos);
            if (level instanceof ServerLevel serverLevel) {
                reader.unlink(serverLevel); // free the computer's endpoint slot
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MediaReaderBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return BlockEntityTickers.create(type, ComputingModule.MEDIA_READER_BE.get(),
                MediaReaderBlockEntity::serverTick);
    }
}
