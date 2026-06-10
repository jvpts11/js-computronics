/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Server's stored items as a type → quantity map, not as 64-per-slot vanilla slots.
 */
public record ServerStorageContents(Map<StorageKey, Long> items) {

    public static final ServerStorageContents EMPTY = new ServerStorageContents(Map.of());

    public ServerStorageContents {
        final Map<StorageKey, Long> kept = new LinkedHashMap<>();
        for (final Map.Entry<StorageKey, Long> entry : items.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        items = Collections.unmodifiableMap(kept);
    }

    /**
     * One persisted line: the data key (item OR fluid, carrying its components) plus the quantity.
     */
    private record Line(StorageKey key, long count) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                StorageKey.CODEC.fieldOf("item").forGetter(Line::key),
                Codec.LONG.fieldOf("count").forGetter(Line::count)
        ).apply(builder, Line::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                StorageKey.STREAM_CODEC, Line::key,
                ByteBufCodecs.VAR_LONG, Line::count,
                Line::new);
    }

    private static ServerStorageContents fromLines(final List<Line> lines) {
        final Map<StorageKey, Long> map = new LinkedHashMap<>();
        for (final Line line : lines) {
            map.merge(line.key(), line.count(), Long::sum);
        }
        return new ServerStorageContents(map);
    }

    private static List<Line> toLines(final ServerStorageContents contents) {
        final List<Line> lines = new ArrayList<>(contents.items.size());
        contents.items.forEach((key, count) -> lines.add(new Line(key, count)));
        return lines;
    }

    public static final Codec<ServerStorageContents> CODEC =
            Line.CODEC.listOf().xmap(ServerStorageContents::fromLines, ServerStorageContents::toLines);

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerStorageContents> STREAM_CODEC =
            Line.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(ServerStorageContents::fromLines, ServerStorageContents::toLines);

    public long total() {
        long sum = 0L;
        for (final long count : items.values()) {
            sum += count;
        }
        return sum;
    }

    public long usedWeight() {
        long sum = 0L;
        for (final Map.Entry<StorageKey, Long> entry : items.entrySet()) {
            sum += entry.getKey().weight(entry.getValue());
        }
        return sum;
    }

    public long count(final StorageKey key) {
        return items.getOrDefault(key, 0L);
    }

    public long count(final Item item) {
        long sum = 0L;
        for (final Map.Entry<StorageKey, Long> entry : items.entrySet()) {
            if (entry.getKey().item() == item) {
                sum += entry.getValue();
            }
        }
        return sum;
    }
}
