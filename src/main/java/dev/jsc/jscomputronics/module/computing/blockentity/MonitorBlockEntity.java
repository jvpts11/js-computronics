/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.peripheral.PeripheralCableType;
import dev.jsc.jscomputronics.common.peripheral.PeripheralEndpoint;
import dev.jsc.jscomputronics.common.peripheral.PeripheralLinkValidator;
import dev.jsc.jscomputronics.common.peripheral.PeripheralOwner;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.PeripheralLinks;
import dev.jsc.jscomputronics.module.computing.block.MonitorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * BlockEntity for a Monitor: a {@code COMPUTING} {@link PeripheralEndpoint} that displays a computer's interface.
 */
public class MonitorBlockEntity extends BlockEntity implements PeripheralEndpoint {

    private static final int BOOT_DELAY = 20;

    @Nullable
    private Long linkedOwner;
    private int bootTicks;

    public MonitorBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.MONITOR_BE.get(), pos, state);
    }

    @Override
    public PeripheralCableType cableType() {
        return PeripheralCableType.COMPUTING;
    }

    @Override
    public Optional<Long> linkedOwner() {
        return Optional.ofNullable(linkedOwner);
    }

    @Override
    public void onOwnerLinked(final long ownerPos) {
        linkedOwner = ownerPos;
        setChanged();
    }

    @Override
    public void onOwnerUnlinked() {
        linkedOwner = null;
        setChanged();
    }

    @Nullable
    public BlockPos ownerPos() {
        return linkedOwner == null ? null : BlockPos.of(linkedOwner);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final MonitorBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        final long self = worldPosition.asLong();
        final PeripheralLinkValidator validator = PeripheralLinks.validator(level);
        if (linkedOwner == null) {
            // Find a reachable computer and link to it (the validator notifies both sides).
            PeripheralLinks.discoverOwner(level, self)
                    .ifPresent(ownerPos -> validator.tryEstablishLink(ownerPos, self));
        } else {
            // Drop the link if the computer is gone or the cable path is broken.
            final boolean ownerPresent =
                    level.getBlockEntity(BlockPos.of(linkedOwner)) instanceof PeripheralOwner;
            if (!ownerPresent
                    || !validator.isLinkStillValid(linkedOwner, self, PeripheralCableType.COMPUTING)) {
                unlink(level);
            }
        }
        updateScreen(level);
    }

    private void updateScreen(final ServerLevel level) {
        final BlockState state = getBlockState();
        if (!state.hasProperty(MonitorBlock.LIT)) {
            return;
        }
        final boolean lit = state.getValue(MonitorBlock.LIT);
        if (linkedOwner == null) {
            bootTicks = 0;
            if (lit) {
                level.setBlock(worldPosition, state.setValue(MonitorBlock.LIT, false), Block.UPDATE_CLIENTS);
            }
            return;
        }
        if (lit) {
            return; // already booted onto the desktop
        }
        if (++bootTicks >= BOOT_DELAY) {
            level.setBlock(worldPosition, state.setValue(MonitorBlock.LIT, true), Block.UPDATE_CLIENTS);
            bootTicks = 0;
        }
    }

    public void unlink(final ServerLevel level) {
        if (linkedOwner != null
                && level.getBlockEntity(BlockPos.of(linkedOwner)) instanceof PeripheralOwner owner) {
            owner.onEndpointUnlinked(worldPosition.asLong());
        }
        onOwnerUnlinked();
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linkedOwner = tag.contains("LinkedOwner") ? tag.getLong("LinkedOwner") : null;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkedOwner != null) {
            tag.putLong("LinkedOwner", linkedOwner);
        }
    }
}
