/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Base class for all {@link SavedData} implementations in the mod.
 */
public abstract class JscSavedData extends SavedData {

    @Override
    public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);
}
