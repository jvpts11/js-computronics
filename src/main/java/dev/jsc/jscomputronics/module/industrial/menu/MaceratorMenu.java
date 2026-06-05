/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial.menu;

import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import dev.jsc.jscomputronics.module.industrial.blockentity.MaceratorBlockEntity;
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
 * Menu for the Macerator: an input slot, an output slot (extract-only) and the player inventory, plus synced progress and FE data for the screen.
 */
public class MaceratorMenu extends AbstractMachineMenu {

    private static final int MACHINE_SLOTS = 2;

    private final MaceratorBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public MaceratorMenu(final int containerId, final Inventory playerInventory,
                         final MaceratorBlockEntity be) {
        super(IndustrialModule.MACERATOR_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        final IItemHandler machine = be.getInventory();
        addSlot(new SlotItemHandler(machine, MaceratorBlockEntity.INPUT_SLOT, 56, 35));
        addSlot(new SlotItemHandler(machine, MaceratorBlockEntity.OUTPUT_SLOT, 116, 35) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return false;
            }
        });

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    public MaceratorMenu(final int containerId, final Inventory playerInventory,
                         final RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static MaceratorBlockEntity resolve(final Inventory inv, final BlockPos pos) {
        return (MaceratorBlockEntity) inv.player.level().getBlockEntity(pos);
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
        return stillValid(access, player, IndustrialModule.MACERATOR.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMove(player, index, MACHINE_SLOTS, MaceratorBlockEntity.INPUT_SLOT);
    }
}
