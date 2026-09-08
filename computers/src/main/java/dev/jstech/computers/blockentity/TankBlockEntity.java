/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.blockentity;

import dev.jstech.computers.ComputingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A plain fluid tank, the minimal external fluid I/O point used to exercise the network's everything-is-data storage: an Import Bus pulls its fluid into the network and an Export Bus pushes fluid back into it, exactly as the same buses move items.
 */
public class TankBlockEntity extends BlockEntity {

    public static final int CAPACITY_MB = 16 * 1000;

    private final FluidTank tank = new FluidTank(CAPACITY_MB) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    };

    public TankBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.TANK_BE.get(), pos, state);
    }

    public IFluidHandler fluidHandler() {
        return tank;
    }

    public FluidStack fluid() {
        return tank.getFluid();
    }

    public float fillFraction() {
        return CAPACITY_MB <= 0 ? 0F : (float) tank.getFluidAmount() / CAPACITY_MB;
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
