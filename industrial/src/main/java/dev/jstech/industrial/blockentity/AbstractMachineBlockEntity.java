/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Base for Category-A industrial machines: an item inventory plus an internal FE buffer, both persisted to NBT.
 */
public abstract class AbstractMachineBlockEntity extends BlockEntity {

    protected final ItemStackHandler inventory;
    protected final MachineEnergyStorage energy;

    protected AbstractMachineBlockEntity(final BlockEntityType<?> type,
                                         final BlockPos pos,
                                         final BlockState state,
                                         final int inventorySize,
                                         final int energyCapacity,
                                         final int energyMaxReceive,
                                         final int energyMaxExtract) {
        super(type, pos, state);
        this.inventory = new ItemStackHandler(inventorySize) {
            @Override
            protected void onContentsChanged(final int slot) {
                setChanged();
            }
        };
        this.energy = new MachineEnergyStorage(
                energyCapacity, energyMaxReceive, energyMaxExtract, this::setChanged);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        if (tag.contains("Energy")) {
            energy.deserializeNBT(registries, tag.get("Energy"));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("Energy", energy.serializeNBT(registries));
    }
}
