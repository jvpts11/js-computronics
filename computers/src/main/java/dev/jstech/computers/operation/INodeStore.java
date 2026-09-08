/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation;

import dev.jstech.computers.storage.StorageKey;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * The slice of a network node's storage the {@link NetworkStorage} aggregator needs: a flat type-quantity view plus extract and insert. A Server's whole store and a Personal Computer's public-only store both expose this so one network view can mix the two.
 */
interface INodeStore {

    Map<StorageKey, Long> view();

    long count(StorageKey key);

    long count(Item item);

    long extract(StorageKey key, long amount);

    /** Inserts up to {@code amount}; a read-only source (a PC's public area) accepts nothing. */
    long insert(StorageKey key, long amount);

    /**
     * How many items this node can hold for the network.
     *
     * <p>For a server that is the whole of its drives. For a personal computer it is only the share its
     * owner published, because the rest of that machine's disks are not the network's to fill.
     */
    long capacity();

    /** How many it is holding, counted the same way. */
    long used();
}
