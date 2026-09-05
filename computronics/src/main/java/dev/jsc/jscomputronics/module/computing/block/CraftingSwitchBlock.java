/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import dev.jsc.jscomputronics.common.util.BlockEntityTickers;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Crafting Switch block: declares up to five adjacent machines for the network, and carries one crafting
 * cable face to its Crafting Computer. Holds a {@link CraftingSwitchBlockEntity} that surveys its faces and
 * discovers the computer over the crafting cable.
 */
public class CraftingSwitchBlock extends Block
        implements EntityBlock, dev.jsc.jscomputronics.common.network.DataNetworkConnectable {

    public CraftingSwitchBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public java.util.Set<dev.jsc.jscomputronics.common.network.DataTier> acceptedCableTiers() {
        // Only the crafting cable attaches (on any face); the switch is not a data-network device.
        return java.util.Set.of(dev.jsc.jscomputronics.common.network.DataTier.CRAFTING);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            final BlockState state, final Level level, final BlockPos pos,
            final net.minecraft.world.entity.player.Player player,
            final net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CraftingSwitchBlockEntity) {
            serverPlayer.openMenu(
                    new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new dev.jsc.jscomputronics.module.computing.menu.CraftingSwitchMenu(
                                    id, inventory, level, pos),
                            net.minecraft.network.chat.Component.translatable("block.jsc.crafting_switch")),
                    buf -> buf.writeBlockPos(pos));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new CraftingSwitchBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return BlockEntityTickers.create(type, ComputingModule.CRAFTING_SWITCH_BE.get(),
                CraftingSwitchBlockEntity::serverTick);
    }
}
