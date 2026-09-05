/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * A {@link NodeStore} over a Server's whole store: a Server is always fully public, so this just forwards to the underlying {@link ServerStore}.
 */
final class ServerNodeStore implements NodeStore {

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
}
