/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.crafting;

import dev.jstech.core.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.storage.DataSink;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.server.level.ServerLevel;

/**
 * Where a machine step draws its inputs from and returns its outputs to. A standalone processing operation uses
 * the network (the default). A machine step run inside a recursive craft uses that craft's isolated pool, so
 * several steps of one craft can run at once and pipeline through the pool without racing on shared network
 * stock — the network is touched only for the craft's raws and its final result.
 */
public interface CraftIo {

    /** Moves up to {@code amount} of {@code key} from this source into {@code into}; returns how much moved. */
    long select(StorageKey key, long amount, DataSink into);

    /** Deposits {@code amount} of {@code key} into this sink; returns how much was stored. */
    long insert(StorageKey key, long amount);

    /** The default I/O: the network's own storage, exactly as processing operations have always used it. */
    static CraftIo network(final ServerLevel level, final NetworkUuid network) {
        return new CraftIo() {
            @Override
            public long select(final StorageKey key, final long amount, final DataSink into) {
                return NetworkStorage.of(level, network).select(key, amount, into);
            }

            @Override
            public long insert(final StorageKey key, final long amount) {
                return NetworkStorage.of(level, network).insert(key, amount);
            }
        };
    }
}
