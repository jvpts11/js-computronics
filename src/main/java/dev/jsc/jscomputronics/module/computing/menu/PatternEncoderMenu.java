/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.PatternEncoderBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.gui.layout.PatternEncoderLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The Pattern Encoder's bay panel: the one media slot, the player's inventory, and two buttons (eject the
 * medium, cancel the queue). Everything the panel displays about the job comes off the block entity, which
 * the server keeps synced; the authoring itself happens on the linked computer's Pattern Studio.
 */
public class PatternEncoderMenu extends AbstractComputerMenu {

    public static final int BUTTON_EJECT = 0;
    public static final int BUTTON_CANCEL = 1;

    public static final int MEDIA_SLOT = 0;

    private final PatternEncoderBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public PatternEncoderMenu(final int containerId, final Inventory playerInventory,
                              final PatternEncoderBlockEntity be) {
        super(ComputingModule.PATTERN_ENCODER_MENU.get(), containerId);
        this.blockEntity = be;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        addSlot(new SlotItemHandler(be.media(), 0, PatternEncoderLayout.MEDIA_X + 1, PatternEncoderLayout.MEDIA_Y + 1) {
            @Override
            public boolean mayPickup(final Player player) {
                return !blockEntity.locked();
            }
        });
        addPlayerInventory(playerInventory, PatternEncoderLayout.INV_X, PatternEncoderLayout.INV_Y);
    }

    @org.jetbrains.annotations.Nullable
    public static PatternEncoderMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                 final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof PatternEncoderBlockEntity be) {
            return new PatternEncoderMenu(containerId, playerInventory, be);
        }
        return null;
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (id == BUTTON_EJECT) {
            if (blockEntity.locked()) {
                return false;
            }
            final ItemStack ejected = blockEntity.ejectMedia();
            if (ejected.isEmpty()) {
                return false;
            }
            if (!player.addItem(ejected)) {
                final BlockPos pos = blockEntity.getBlockPos();
                Containers.dropItemStack(player.level(), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, ejected);
            }
            return true;
        }
        if (id == BUTTON_CANCEL) {
            blockEntity.cancelAll();
            return true;
        }
        return false;
    }

    public PatternEncoderBlockEntity blockEntity() {
        return blockEntity;
    }

    public BlockPos blockEntityPos() {
        return blockEntity.getBlockPos();
    }

    public ItemStack mediaStack() {
        return slots.get(MEDIA_SLOT).getItem();
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMoveBetweenContainerAndPlayer(player, index, 1);
    }

    @Override
    public boolean stillValid(final Player player) {
        // Every era's encoder shares this menu, so validity keys on the family, not one block.
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof PatternEncoderBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }
}
