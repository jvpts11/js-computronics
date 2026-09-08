/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Several machine faces acting as one port. A sided machine may spread its inputs and outputs over different
 * faces (a separator gives hydrogen on one side and oxygen on the other); a bus on each face joins them here,
 * so the engine feeds and collects through all of them without caring which face holds what. Transfers try the
 * faces in order and stop as soon as the amount is satisfied; counts and listings are unions.
 */
public final class CompositeDataPort implements IDataPort {

    private final List<IDataPort> faces;

    private CompositeDataPort(final List<? extends IDataPort> faces) {
        this.faces = List.copyOf(faces);
    }

    /** One face is returned as is; several are joined. An empty list yields an empty port. */
    public static IDataPort of(final List<? extends IDataPort> faces) {
        return faces.size() == 1 ? faces.get(0) : new CompositeDataPort(faces);
    }

    @Override
    public boolean isEmpty() {
        for (final IDataPort face : faces) {
            if (!face.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public long insert(final StorageKey key, final long amount, final boolean simulate) {
        long moved = 0L;
        for (final IDataPort face : faces) {
            if (moved >= amount) {
                break;
            }
            moved += face.insert(key, amount - moved, simulate);
        }
        return moved;
    }

    @Override
    public long extract(final StorageKey key, final long amount, final boolean simulate) {
        long moved = 0L;
        for (final IDataPort face : faces) {
            if (moved >= amount) {
                break;
            }
            moved += face.extract(key, amount - moved, simulate);
        }
        return moved;
    }

    @Override
    public long count(final StorageKey key) {
        /*
         * Faces of one machine usually see the same tanks and slots, so the count is the largest view, not the
         * sum: a tank visible from two faces still holds its contents once.
         */
        long most = 0L;
        for (final IDataPort face : faces) {
            most = Math.max(most, face.count(key));
        }
        return most;
    }

    @Override
    public List<StorageKey> available() {
        final Set<StorageKey> keys = new LinkedHashSet<>();
        for (final IDataPort face : faces) {
            keys.addAll(face.available());
        }
        return new ArrayList<>(keys);
    }
}
