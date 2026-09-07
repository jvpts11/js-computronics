/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block.part;

import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * A thin device attached to one face of a data cable, in the AE2 sense: the cable and up to six parts share a single block position, and a part interacts with the block its mounted face points at (an adjacent inventory, for the buses).
 */
public sealed interface ICablePart permits AbstractBusPart {

    CablePartType type();

    void attach(DataCableBlockEntity host, Direction face);

    void serverTick();

    void save(CompoundTag tag, HolderLookup.Provider registries);

    void load(CompoundTag tag, HolderLookup.Provider registries);

    ItemStack partItem();

    default void dropContents(ServerLevel level) {
    }

    default boolean hasMenu() {
        return false;
    }
}
