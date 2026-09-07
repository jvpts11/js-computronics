/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.storage.LocalStore;
import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * An {@link INodeStore} exposing only the published share of a Personal Computer's disk-backed storage. The network can SELECT from a PC's public area but never write into it (a PC is a read-only source), so {@link #insert} accepts nothing and the private remainder is invisible here.
 */
final class PcPublicNodeStore implements INodeStore {

    private final LocalStore store;

    PcPublicNodeStore(final LocalStore store) {
        this.store = store;
    }

    @Override
    public Map<StorageKey, Long> view() {
        return store.publicView();
    }

    @Override
    public long count(final StorageKey key) {
        return store.publicView().getOrDefault(key, 0L);
    }

    @Override
    public long count(final Item item) {
        long sum = 0L;
        for (final Map.Entry<StorageKey, Long> entry : store.publicView().entrySet()) {
            if (entry.getKey().item() == item) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    @Override
    public long extract(final StorageKey key, final long amount) {
        return store.extractPublic(key, amount);
    }

    @Override
    public long insert(final StorageKey key, final long amount) {
        return 0L; // a PC's public area is a SELECT-source only, never an INSERT target
    }

    @Override
    public long capacity() {
        return store.publicCapacity();
    }

    @Override
    public long used() {
        long sum = 0L;
        for (final Long held : store.publicView().values()) {
            sum += held;
        }
        return sum;
    }
}
