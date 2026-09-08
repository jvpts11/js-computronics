/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A port restricted to a single kind of data. Wrapping a machine face with the filter its bus carries lets the
 * player pin what each face handles: a Chemical Infuser fed two chemicals from two sides routes each ingredient
 * to the correct face instead of relying on the machine to reject the wrong one. A {@code null} filter passes
 * everything, so an unfiltered bus behaves exactly like the raw face.
 */
public final class FilteredDataPort implements IDataPort {

    private final IDataPort delegate;
    @Nullable
    private final StorageKey filter;

    public FilteredDataPort(final IDataPort delegate, @Nullable final StorageKey filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    private boolean passes(final StorageKey key) {
        return filter == null || filter.equals(key);
    }

    @Override
    public boolean isEmpty() {
        /*
         * Emptiness is about whether the face is reachable at all, not about the filter: a filtered but present
         * face is still a live port, so the engine keeps it in the composite.
         */
        return delegate.isEmpty();
    }

    @Override
    public long insert(final StorageKey key, final long amount, final boolean simulate) {
        return passes(key) ? delegate.insert(key, amount, simulate) : 0L;
    }

    @Override
    public long extract(final StorageKey key, final long amount, final boolean simulate) {
        return passes(key) ? delegate.extract(key, amount, simulate) : 0L;
    }

    @Override
    public long count(final StorageKey key) {
        return passes(key) ? delegate.count(key) : 0L;
    }

    @Override
    public List<StorageKey> available() {
        if (filter == null) {
            return delegate.available();
        }
        final List<StorageKey> kept = new ArrayList<>();
        for (final StorageKey key : delegate.available()) {
            if (filter.equals(key)) {
                kept.add(key);
            }
        }
        return kept;
    }
}
