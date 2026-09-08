/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.network;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Marker implemented by blocks that a data cable connects to, network devices such as the Mainframe, routers and racks.
 */
public interface IDataNetworkConnectable {

    default Set<DataTier> acceptedCableTiers() {
        return Set.of(DataTier.values());
    }

    /**
     * Whether this device accepts a data cable touching the given face of its own block. Defaults to
     * every face; a standalone computer narrows this to its rear so its data port is on the back only.
     * The single source of truth for both the cable's rendered connection and the device's own
     * network attachment, so the two can never disagree.
     *
     * @param state the device's current block state (carries its facing)
     * @param face  the face of THIS block that the cable touches
     */
    default boolean connectsOnFace(final BlockState state, final Direction face) {
        return true;
    }

    /**
     * Tier-aware variant the cable itself asks: some devices accept different cable families on different
     * faces (a Crafting Computer takes the data cable on its rear only, but the crafting cable on any face).
     * Defaults to the face-only rule so existing devices are unaffected.
     *
     * @param state the device's current block state (carries its facing)
     * @param face  the face of THIS block that the cable touches
     * @param tier  the tier of the touching cable
     */
    default boolean connectsOnFace(final BlockState state, final Direction face, final DataTier tier) {
        return connectsOnFace(state, face);
    }
}
