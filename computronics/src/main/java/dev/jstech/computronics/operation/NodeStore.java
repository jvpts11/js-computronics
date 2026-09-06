/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * The slice of a network node's storage the {@link NetworkStorage} aggregator needs: a flat type-quantity view plus extract and insert. A Server's whole store and a Personal Computer's public-only store both expose this so one network view can mix the two.
 */
interface NodeStore {

    Map<StorageKey, Long> view();

    long count(StorageKey key);

    long count(Item item);

    long extract(StorageKey key, long amount);

    /** Inserts up to {@code amount}; a read-only source (a PC's public area) accepts nothing. */
    long insert(StorageKey key, long amount);
}
