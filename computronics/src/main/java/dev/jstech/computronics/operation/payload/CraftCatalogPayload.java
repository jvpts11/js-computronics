/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server to client: the network's craft catalog: every distinct result the Recipe ROMs of its Crafting Computers can produce, with an at-a-glance availability dot computed against current stock.
 */
public record CraftCatalogPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final byte DOT_GREEN = 2;
    public static final byte DOT_AMBER = 1;
    public static final byte DOT_RED = 0;

    public static final int MAX_ENTRIES = 512;
    /** The most characters of a recipe's own name an entry carries. */
    public static final int MAX_LABEL = 64;

    /**
     * One craftable result.
     *
     * @param result       what the recipe makes
     * @param availability the stock dot: green, amber or red
     * @param multiStage   whether a multi-stage pipeline makes it
     * @param label        the name the recipe's author gave it, or {@code ""} when it goes by its result
     */
    public record Entry(ItemStack result, byte availability, boolean multiStage, String label) {

        public Entry(final ItemStack result, final byte availability, final boolean multiStage) {
            this(result, availability, multiStage, "");
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ItemStack.STREAM_CODEC, Entry::result,
                        ByteBufCodecs.BYTE, Entry::availability,
                        ByteBufCodecs.BOOL, Entry::multiStage,
                        ByteBufCodecs.stringUtf8(MAX_LABEL), Entry::label,
                        Entry::new);

        /** What the entry is listed as: the recipe's own name, or its result's name when it has none. */
        public String title() {
            return label.isEmpty() ? result.getHoverName().getString() : label;
        }
    }

    public static final CustomPacketPayload.Type<CraftCatalogPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "craft_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftCatalogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), CraftCatalogPayload::entries,
                    CraftCatalogPayload::new);

    @Override
    public CustomPacketPayload.Type<CraftCatalogPayload> type() {
        return TYPE;
    }
}
