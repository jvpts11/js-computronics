/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial.blockentity;

import dev.jstech.core.util.FieldContainerData;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The Coal Generator — the first FE source: burns furnace fuel (coal, charcoal, etc.) to produce {@value #FE_PER_TICK} FE per tick while it has burn time left, and pushes stored FE into adjacent energy consumers each tick.
 */
public class CoalGeneratorBlockEntity extends AbstractMachineBlockEntity {

    public static final int FUEL_SLOT = 0;
    public static final int FE_PER_TICK = 20;
    private static final int ENERGY_CAPACITY = 16_000;
    private static final int ENERGY_MAX_EXTRACT = 1_000;

    private int burnTime;
    private int maxBurnTime;

    public CoalGeneratorBlockEntity(final BlockPos pos, final BlockState state) {
        super(IndustrialModule.COAL_GENERATOR_BE.get(), pos, state,
                1, ENERGY_CAPACITY, 0, ENERGY_MAX_EXTRACT);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final CoalGeneratorBlockEntity be) {
        be.tick(level);
    }

    private void tick(final Level level) {
        boolean changed = false;
        final boolean hasRoom = energy.getEnergyStored() < energy.getMaxEnergyStored();

        if (burnTime > 0) {
            burnTime--;
            if (hasRoom) {
                energy.generate(FE_PER_TICK);
            }
            changed = true;
        } else if (hasRoom) {
            final ItemStack fuel = inventory.getStackInSlot(FUEL_SLOT);
            final int duration = fuel.getBurnTime(null);
            if (duration > 0) {
                inventory.extractItem(FUEL_SLOT, 1, false);
                burnTime = duration;
                maxBurnTime = duration;
                energy.generate(FE_PER_TICK);
                changed = true;
            }
        }

        pushEnergy(level);

        if (changed) {
            setChanged();
        }
    }

    private void pushEnergy(final Level level) {
        for (final Direction direction : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                break;
            }
            final IEnergyStorage neighbor = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(direction),
                    direction.getOpposite());
            if (neighbor == null || !neighbor.canReceive()) {
                continue;
            }
            final int offered = energy.extractEnergy(ENERGY_MAX_EXTRACT, true);
            final int accepted = neighbor.receiveEnergy(offered, false);
            if (accepted > 0) {
                energy.extractEnergy(accepted, false);
            }
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTime = tag.getInt("BurnTime");
        maxBurnTime = tag.getInt("MaxBurnTime");
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("MaxBurnTime", maxBurnTime);
    }

    public int getBurnTime() {
        return burnTime;
    }

    public int getMaxBurnTime() {
        return maxBurnTime;
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    private final ContainerData dataAccess = new FieldContainerData(
            new IntSupplier[] {
                () -> burnTime,
                () -> maxBurnTime,
                () -> energy.getEnergyStored(),
            },
            new IntConsumer[] {
                value -> burnTime = value,
                value -> maxBurnTime = value,
                value -> energy.setEnergyStored(value),
            });

    public ContainerData getDataAccess() {
        return dataAccess;
    }
}
