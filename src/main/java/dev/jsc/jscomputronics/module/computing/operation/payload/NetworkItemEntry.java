/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One row in the network/storage view: the data type held (item OR fluid, via {@link StorageKey}) and the true total on the network, which may exceed a stack/bucket.
 */
public record NetworkItemEntry(StorageKey key, long total) {

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkItemEntry> STREAM_CODEC =
            StreamCodec.composite(
                    StorageKey.STREAM_CODEC, NetworkItemEntry::key,
                    ByteBufCodecs.VAR_LONG, NetworkItemEntry::total,
                    NetworkItemEntry::new);

    public boolean isFluid() {
        return key.isFluid();
    }

    public ItemStack icon() {
        return key.stack(1);
    }

    public Component name() {
        return key.displayName();
    }
}
