/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

/**
 * A {@link DataSink} over a computer's disk-backed {@link LocalStore}, so a timed SELECT/MOVE can stream any data (items or fluids) into local storage, bounded by the installed disks' free data weight.
 */
public final class LocalStoreSink implements DataSink {

    private final LocalStore store;

    public LocalStoreSink(final LocalStore store) {
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
