/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A data device whose cable port sits on its rear face only: every standalone computer and every rear-connecting multiblock. The block must carry the standard horizontal {@code FACING} property; the cable attaches on the face opposite FACING.
 *
 * <p>Implementing this instead of overriding {@link IDataNetworkConnectable#connectsOnFace} per block keeps the rear-only rule in one place, so the cable's rendered connection and the device's network attachment can never drift apart. {@link IDataNetworkConnectable} itself defaults to accepting every face (the Mainframe's behaviour); this narrows that to the rear.
 */
public interface IRearFacingDataPort extends IDataNetworkConnectable {

    @Override
    default boolean connectsOnFace(final BlockState state, final Direction face) {
        return face == state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
    }
}
