/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A block face as the network sees it: the {@link IDataChannel} of every kind of data the block offers there,
 * so "everything is data" meets a real machine in one place. Production code builds ports with
 * {@link #at(Level, BlockPos, Direction)}, which asks {@link DataChannels} for every kind; the handler
 * constructors exist for tests and for callers that already hold a specific capability.
 */
public final class ExternalDataPort implements IDataPort {

    private final Map<StorageKey.Kind, IDataChannel> channels;

    private ExternalDataPort(final Map<StorageKey.Kind, IDataChannel> channels) {
        this.channels = channels;
    }

    /** The port onto the block at {@code pos} as seen from {@code side}, with every kind of data it offers. */
    public static ExternalDataPort at(final Level level, final BlockPos pos, @Nullable final Direction side) {
        return new ExternalDataPort(DataChannels.resolve(level, pos, side));
    }

    public ExternalDataPort(@Nullable final IItemHandler items, @Nullable final IFluidHandler fluids) {
        this(items, fluids, null);
    }

    public ExternalDataPort(@Nullable final IItemHandler items, @Nullable final IFluidHandler fluids,
                            @Nullable final IChemicalPort chemicals) {
        this.channels = new EnumMap<>(StorageKey.Kind.class);
        if (items != null) {
            channels.put(StorageKey.Kind.ITEM, new ItemChannel(items));
        }
        if (fluids != null) {
            channels.put(StorageKey.Kind.FLUID, new FluidChannel(fluids));
        }
        if (chemicals != null) {
            channels.put(StorageKey.Kind.CHEMICAL, new ChemicalChannel(chemicals));
        }
    }

    /** The kinds of data this face offers. */
    public Set<StorageKey.Kind> kinds() {
        return channels.keySet();
    }

    @Override
    public boolean isEmpty() {
        return channels.isEmpty();
    }

    @Nullable
    private IDataChannel channel(final StorageKey key) {
        return channels.get(key.kind());
    }

    @Override
    public long insert(final StorageKey key, final long amount, final boolean simulate) {
        final IDataChannel channel = channel(key);
        return channel == null ? 0L : channel.insert(key, amount, simulate);
    }

    @Override
    public long extract(final StorageKey key, final long amount, final boolean simulate) {
        final IDataChannel channel = channel(key);
        return channel == null ? 0L : channel.extract(key, amount, simulate);
    }

    @Override
    public long count(final StorageKey key) {
        final IDataChannel channel = channel(key);
        return channel == null ? 0L : channel.count(key);
    }

    @Override
    public List<StorageKey> available() {
        final List<StorageKey> keys = new ArrayList<>();
        for (final IDataChannel channel : channels.values()) {
            keys.addAll(channel.available());
        }
        return keys;
    }
}
