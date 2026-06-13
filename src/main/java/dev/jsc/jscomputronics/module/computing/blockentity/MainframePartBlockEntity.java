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
import net.minecraft.world.level.block.state.BlockState;

/**
 * A structural part of the Mainframe multiblock.
 */
public class MainframePartBlockEntity extends MultiblockPartBlockEntity {

    public MainframePartBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.MAINFRAME_PART_BE.get(), pos, state);
    }
}
