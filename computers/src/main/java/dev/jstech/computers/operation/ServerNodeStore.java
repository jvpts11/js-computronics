/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation;

import dev.jstech.computers.storage.ServerStore;
import dev.jstech.computers.storage.StorageKey;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * An {@link INodeStore} over a Server's whole store: a Server is always fully public, so this just forwards to the underlying {@link ServerStore}.
 */
final class ServerNodeStore implements INodeStore {

    private final ServerStore store;

    ServerNodeStore(final ServerStore store) {
        this.store = store;
    }

    @Override
    public Map<StorageKey, Long> view() {
        return store.view();
    }

    @Override
    public long count(final StorageKey key) {
        return store.count(key);
    }

    @Override
    public long count(final Item item) {
        return store.count(item);
    }

    @Override
    public long extract(final StorageKey key, final long amount) {
        return store.extract(key, amount);
    }

    @Override
    public long insert(final StorageKey key, final long amount) {
        return store.insert(key, amount);
    }

    @Override
    public long capacity() {
        return store.capacity();
    }

    @Override
    public long used() {
        return store.used();
    }
}
