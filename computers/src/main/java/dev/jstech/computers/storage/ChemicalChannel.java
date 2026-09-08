/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A block face's chemical port (from an {@link IChemicalBridge}), as a data channel; amounts are millibuckets. */
public record ChemicalChannel(IChemicalPort chemicals) implements IDataChannel {

    @Override
    public StorageKey.Kind kind() {
        return StorageKey.Kind.CHEMICAL;
    }

    @Override
    public long insert(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L || !key.isChemical()) {
            return 0L;
        }
        return chemicals.fill(Objects.requireNonNull(key.chemicalId()), amount, simulate);
    }

    @Override
    public long extract(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L || !key.isChemical()) {
            return 0L;
        }
        return chemicals.drain(Objects.requireNonNull(key.chemicalId()), amount, simulate);
    }

    @Override
    public long count(final StorageKey key) {
        return key.isChemical() ? chemicals.count(Objects.requireNonNull(key.chemicalId())) : 0L;
    }

    @Override
    public List<StorageKey> available() {
        final List<StorageKey> keys = new ArrayList<>();
        for (final ResourceLocation chemical : chemicals.available()) {
            keys.add(StorageKey.chemical(chemical));
        }
        return keys;
    }
}
