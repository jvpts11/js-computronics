/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The one place that knows how each {@link StorageKey.Kind} is found on a block face. Every kind must have a
 * resolver here (the class refuses to load otherwise) so adding a kind of data to {@link StorageKey} forces
 * its channel into every port the network builds, instead of into whichever call sites happened to be
 * updated.
 */
public final class DataChannels {

    private DataChannels() {
    }

    /** Finds a kind's channel on a block face, or null when the block offers none of that kind there. */
    @FunctionalInterface
    public interface IResolver {
        @Nullable
        IDataChannel resolve(Level level, BlockPos pos, @Nullable Direction side);
    }

    private static final Map<StorageKey.Kind, IResolver> RESOLVERS = new EnumMap<>(StorageKey.Kind.class);

    static {
        RESOLVERS.put(StorageKey.Kind.ITEM, (level, pos, side) -> {
            final IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
            return items == null ? null : new ItemChannel(items);
        });
        RESOLVERS.put(StorageKey.Kind.FLUID, (level, pos, side) -> {
            final IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
            return fluids == null ? null : new FluidChannel(fluids);
        });
        RESOLVERS.put(StorageKey.Kind.CHEMICAL, (level, pos, side) ->
                ChemicalBridges.portFor(level, pos, side).<IDataChannel>map(ChemicalChannel::new).orElse(null));
        for (final StorageKey.Kind kind : StorageKey.Kind.values()) {
            if (!RESOLVERS.containsKey(kind)) {
                throw new IllegalStateException("no data channel resolver for " + kind + ": every kind of data must be transferable");
            }
        }
    }

    /** Every channel the block at {@code pos} offers on {@code side}, keyed by kind; absent kinds are omitted. */
    public static Map<StorageKey.Kind, IDataChannel> resolve(final Level level, final BlockPos pos, @Nullable final Direction side) {
        final Map<StorageKey.Kind, IDataChannel> channels = new EnumMap<>(StorageKey.Kind.class);
        for (final Map.Entry<StorageKey.Kind, IResolver> entry : RESOLVERS.entrySet()) {
            final IDataChannel channel = entry.getValue().resolve(level, pos, side);
            if (channel != null) {
                channels.put(entry.getKey(), channel);
            }
        }
        return channels;
    }
}
