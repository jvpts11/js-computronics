/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

/**
 * An {@link IDataSink} over any {@link IWeightedStore}, so a timed SELECT/MOVE can stream any data (items or fluids) into a Server's store or a computer's local storage, bounded by the store's free data weight.
 */
public final class StoreSink implements IDataSink {

    private final IWeightedStore store;

    public StoreSink(final IWeightedStore store) {
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
