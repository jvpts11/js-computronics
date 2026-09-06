/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.menu;

import dev.jstech.industrial.IndustrialModule;
import dev.jstech.industrial.blockentity.CompressorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Compressor: an input slot, an extract-only output slot, and the player inventory,
 * plus synced progress and FE data for the screen.
 */
public class CompressorMenu extends AbstractMachineMenu {

    private static final int MACHINE_SLOTS = 2;

    private final CompressorBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public CompressorMenu(final int containerId, final Inventory playerInventory,
                          final CompressorBlockEntity be) {
        super(IndustrialModule.COMPRESSOR_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        final IItemHandler machine = be.getInventory();
        addSlot(new SlotItemHandler(machine, CompressorBlockEntity.INPUT_SLOT, 56, 35));
        addSlot(new SlotItemHandler(machine, CompressorBlockEntity.OUTPUT_SLOT, 116, 35) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return false;
            }
        });

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    public CompressorMenu(final int containerId, final Inventory playerInventory,
                          final RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static CompressorBlockEntity resolve(final Inventory inv, final BlockPos pos) {
        return (CompressorBlockEntity) inv.player.level().getBlockEntity(pos);
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    public int getEnergy() {
        return data.get(2);
    }

    public int getMaxEnergy() {
        return blockEntity.getEnergy().getMaxEnergyStored();
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, IndustrialModule.COMPRESSOR.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMove(player, index, MACHINE_SLOTS, CompressorBlockEntity.INPUT_SLOT);
    }
}
