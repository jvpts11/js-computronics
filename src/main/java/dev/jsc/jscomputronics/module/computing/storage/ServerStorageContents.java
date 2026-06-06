/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Server's stored items as a type → quantity map, not as 64-per-slot vanilla slots.
 */
public record ServerStorageContents(Map<Item, Long> items) {

    public static final ServerStorageContents EMPTY = new ServerStorageContents(Map.of());

    public ServerStorageContents {
        final Map<Item, Long> kept = new LinkedHashMap<>();
        for (final Map.Entry<Item, Long> entry : items.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        items = Collections.unmodifiableMap(kept);
    }

    public static final Codec<ServerStorageContents> CODEC =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.LONG)
                    .xmap(ServerStorageContents::new, ServerStorageContents::items);

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerStorageContents> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.registry(Registries.ITEM), ByteBufCodecs.VAR_LONG)
                    .map(ServerStorageContents::new, sc -> new HashMap<>(sc.items()));

    public long total() {
        long sum = 0L;
        for (final long count : items.values()) {
            sum += count;
        }
        return sum;
    }

    public long count(final Item item) {
        return items.getOrDefault(item, 0L);
    }
}
