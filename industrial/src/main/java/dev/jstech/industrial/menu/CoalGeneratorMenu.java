/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.menu;

import dev.jstech.industrial.IndustrialModule;
import dev.jstech.industrial.blockentity.CoalGeneratorBlockEntity;
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
 * Menu for the Coal Generator: a fuel slot plus the player inventory, with synced burn progress and FE data for the screen.
 */
public class CoalGeneratorMenu extends AbstractMachineMenu {

    private static final int MACHINE_SLOTS = 1;

    private final CoalGeneratorBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public CoalGeneratorMenu(final int containerId, final Inventory playerInventory,
                             final CoalGeneratorBlockEntity be) {
        super(IndustrialModule.COAL_GENERATOR_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        final IItemHandler machine = be.getInventory();
        addSlot(new SlotItemHandler(machine, CoalGeneratorBlockEntity.FUEL_SLOT, 80, 53));

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    public CoalGeneratorMenu(final int containerId, final Inventory playerInventory,
                             final RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static CoalGeneratorBlockEntity resolve(final Inventory inv, final BlockPos pos) {
        return (CoalGeneratorBlockEntity) inv.player.level().getBlockEntity(pos);
    }

    public int getBurnScaled() {
        final int max = data.get(1);
        return max > 0 ? data.get(0) * 13 / max : 0;
    }

    public boolean isBurning() {
        return data.get(0) > 0;
    }

    public int getEnergy() {
        return data.get(2);
    }

    public int getMaxEnergy() {
        return blockEntity.getEnergy().getMaxEnergyStored();
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, IndustrialModule.COAL_GENERATOR.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMove(player, index, MACHINE_SLOTS, CoalGeneratorBlockEntity.FUEL_SLOT);
    }
}
