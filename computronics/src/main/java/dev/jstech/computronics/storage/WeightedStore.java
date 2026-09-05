/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

/**
 * A store that holds data by weight and reports how much room it has left — the contract a {@link StoreSink} needs, shared by a Server's {@link ServerStore} and a computer's disk-backed {@link LocalStore} so one sink serves both.
 */
public interface WeightedStore {

    /** The free data weight (in mB-equivalents) the store can still accept. */
    long freeWeight();

    /** Stores up to {@code amount} of the key, returning how much was actually stored. */
    long insert(StorageKey key, long amount);
}
