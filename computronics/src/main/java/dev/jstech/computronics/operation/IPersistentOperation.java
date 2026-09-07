/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * An in-flight {@link INetworkOperation} that survives the world being saved and reopened. The Mainframe
 * writes every live persistent operation into its own NBT on save and rebuilds them on the first booted
 * tick after a load, so a craft that was running when the player left the world is still running when
 * they come back — instead of silently vanishing with the chunk.
 *
 * <p>The {@code Kind} written by {@link #saveState} selects the restorer; the id lets a multi-stage
 * pipeline find the stage it was waiting on among the restored operations.
 */
public interface IPersistentOperation extends INetworkOperation {

    String KIND_KEY = "Kind";
    String ID_KEY = "Id";

    UUID operationId();

    /** The state needed to resume this operation later; includes {@link #KIND_KEY} and {@link #ID_KEY}. */
    CompoundTag saveState(HolderLookup.Provider registries);

    /**
     * True when this operation must NOT be written to disk on save. A machine step run inside a recursive craft
     * is ephemeral: it reads and writes the parent craft's in-memory pool, which does not survive a reload, so
     * it is abandoned on save and the parent craft re-plans and re-creates it. Ordinary operations return false
     * and persist as before.
     */
    default boolean isEphemeral() {
        return false;
    }
}
