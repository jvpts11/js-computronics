/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import dev.jstech.computers.storage.StorageKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One row in the network/storage view: the data type held (item OR fluid, via {@link StorageKey}), the true
 * total on the network (which may exceed a stack/bucket), and where it is stored ({@link #shares}, one entry
 * per server/storage that holds it, for the details panel; empty for views without a per-server breakdown).
 */
public record NetworkItemEntry(StorageKey key, long total, List<StorageShare> shares) {

    /** Where a data type lives: a server/storage label and how much of the type that one holds. */
    public record StorageShare(String label, long qty) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StorageShare> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, StorageShare::label,
                        ByteBufCodecs.VAR_LONG, StorageShare::qty,
                        StorageShare::new);
    }

    /** Wire cap on the per-server breakdown so encoding never overflows; a type in more servers is truncated. */
    public static final int MAX_SHARES = 64;

    /** Convenience for views without a per-server breakdown (local storage, the terminal). */
    public NetworkItemEntry(final StorageKey key, final long total) {
        this(key, total, List.of());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkItemEntry> STREAM_CODEC =
            StreamCodec.composite(
                    StorageKey.STREAM_CODEC, NetworkItemEntry::key,
                    ByteBufCodecs.VAR_LONG, NetworkItemEntry::total,
                    StorageShare.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SHARES)), NetworkItemEntry::shares,
                    NetworkItemEntry::new);

    public boolean isFluid() {
        return key.isFluid();
    }

    public boolean isChemical() {
        return key.isChemical();
    }

    public ItemStack icon() {
        return key.stack(1);
    }

    public Component name() {
        return key.displayName();
    }
}
