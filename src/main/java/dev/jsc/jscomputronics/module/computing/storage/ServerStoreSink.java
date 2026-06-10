/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

/**
 * A {@link DataSink} over a single Server's {@link ServerStore}, so a timed SELECT/MOVE can stream any data (items or fluids) into that Server, bounded by its disks' free data weight.
 */
public final class ServerStoreSink implements DataSink {

    private final ServerStore store;

    public ServerStoreSink(final ServerStore store) {
        this.store = store;
    }

    @Override
    public long insert(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        if (simulate) {
            return Math.min(amount, store.freeWeight() / key.weight(1L));
        }
        return store.insert(key, amount);
    }
}
