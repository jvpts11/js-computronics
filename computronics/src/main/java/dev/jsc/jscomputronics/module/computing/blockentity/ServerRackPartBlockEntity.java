/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jstech.core.multiblock.MultiblockPartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A structural part of the Server Rack multiblock.
 */
public class ServerRackPartBlockEntity extends MultiblockPartBlockEntity {

    public ServerRackPartBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.SERVER_RACK_PART_BE.get(), pos, state);
    }
}
