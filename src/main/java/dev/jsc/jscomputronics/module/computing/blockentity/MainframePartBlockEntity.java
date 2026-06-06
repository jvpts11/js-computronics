/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A structural part of the Mainframe multiblock.
 */
public class MainframePartBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos controllerPos;

    public MainframePartBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.MAINFRAME_PART_BE.get(), pos, state);
    }

    @Nullable
    public BlockPos controllerPos() {
        return controllerPos;
    }

    public void setController(final BlockPos pos) {
        this.controllerPos = pos.immutable();
        setChanged();
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ControllerX")) {
            controllerPos = new BlockPos(tag.getInt("ControllerX"),
                    tag.getInt("ControllerY"), tag.getInt("ControllerZ"));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putInt("ControllerX", controllerPos.getX());
            tag.putInt("ControllerY", controllerPos.getY());
            tag.putInt("ControllerZ", controllerPos.getZ());
        }
    }
}
