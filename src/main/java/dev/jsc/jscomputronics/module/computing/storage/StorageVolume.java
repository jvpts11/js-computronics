/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The stored items of one drive, kept in the level's volume store rather than on the item: a mutable
 * key → quantity map with its data weight and item count carried alongside, so an insert or extract is
 * one map update however much the drive holds. The drive item itself carries only the volume's id and
 * a small usage summary, which is what keeps a full drive cheap to copy, compare, sync and save.
 */
public final class StorageVolume {

    /** A blank volume that refuses writes: what a drive without one, or a client, sees. */
    public static final StorageVolume EMPTY = new StorageVolume(new UUID(0L, 0L), () -> { }, true);

    private final UUID id;
    private final Runnable dirty;
    private final boolean readOnly;
    private final Map<StorageKey, Long> items = new LinkedHashMap<>();
    private long usedWeight;
    // Running totals per kind, kept alongside the map so the usage summary never walks the contents:
    // a drive holding thousands of types is written to on every insert, and that walk was the cost the
    // volume store exists to avoid.
    private long itemUnits;
    private long fluidUnits;
    private long chemicalUnits;

    StorageVolume(final UUID id, final Runnable dirty, final boolean readOnly) {
        this.id = id;
        this.dirty = dirty;
        this.readOnly = readOnly;
    }

    public UUID id() {
        return id;
    }

    /** The contents, read-only and live: a snapshot must be taken to keep it across a write. */
    public Map<StorageKey, Long> items() {
        return Collections.unmodifiableMap(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** How many distinct keys the volume holds. */
    public int types() {
        return items.size();
    }

    /** How many units (items, or millibuckets) the volume holds in all. */
    public long total() {
        return itemUnits + fluidUnits + chemicalUnits;
    }

    /** How many items the volume holds, by the piece. */
    public long itemUnits() {
        return itemUnits;
    }

    /** How much fluid the volume holds, in millibuckets. */
    public long fluidUnits() {
        return fluidUnits;
    }

    /** How much chemical the volume holds, in millibuckets. */
    public long chemicalUnits() {
        return chemicalUnits;
    }

    /** The data weight of everything stored, in the same units a disk's capacity is measured in. */
    public long usedWeight() {
        return usedWeight;
    }

    private void tally(final StorageKey key, final long delta) {
        usedWeight += key.weight(delta);
        switch (key.kind()) {
            case ITEM -> itemUnits += delta;
            case FLUID -> fluidUnits += delta;
            case CHEMICAL -> chemicalUnits += delta;
        }
    }

    public long count(final StorageKey key) {
        return items.getOrDefault(key, 0L);
    }

    /** Adds {@code amount} of {@code key}; returns the key's new count. The caller checks the room. */
    public long add(final StorageKey key, final long amount) {
        if (readOnly || amount <= 0L) {
            return count(key);
        }
        final long now = items.merge(key, amount, Long::sum);
        tally(key, amount);
        dirty.run();
        return now;
    }

    /** Takes up to {@code amount} of {@code key}; returns how many came out. */
    public long take(final StorageKey key, final long amount) {
        if (readOnly || amount <= 0L) {
            return 0L;
        }
        final long have = count(key);
        if (have <= 0L) {
            return 0L;
        }
        final long taken = Math.min(have, amount);
        if (taken == have) {
            items.remove(key);
        } else {
            items.put(key, have - taken);
        }
        tally(key, -taken);
        dirty.run();
        return taken;
    }

    /** Empties the volume. */
    public void clear() {
        if (readOnly || items.isEmpty()) {
            return;
        }
        items.clear();
        resetTallies();
        dirty.run();
    }

    private void resetTallies() {
        usedWeight = 0L;
        itemUnits = 0L;
        fluidUnits = 0L;
        chemicalUnits = 0L;
    }

    /** Replaces the whole contents; entries that are not positive are dropped. */
    public void replaceAll(final Map<StorageKey, Long> contents) {
        if (readOnly) {
            return;
        }
        items.clear();
        resetTallies();
        for (final Map.Entry<StorageKey, Long> entry : contents.entrySet()) {
            final Long count = entry.getValue();
            if (count != null && count > 0L) {
                items.put(entry.getKey(), count);
                tally(entry.getKey(), count);
            }
        }
        dirty.run();
    }

    /** An immutable copy of the contents, for code that reads a whole drive at once. */
    public ServerStorageContents snapshot() {
        return new ServerStorageContents(items);
    }
}
