/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server to client: the network's craft catalog — every distinct result the Recipe ROMs of its Crafting Computers can produce, with an at-a-glance availability dot computed against current stock.
 */
public record CraftCatalogPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final byte DOT_GREEN = 2;
    public static final byte DOT_AMBER = 1;
    public static final byte DOT_RED = 0;

    public static final int MAX_ENTRIES = 512;

    public record Entry(ItemStack result, byte availability) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ItemStack.STREAM_CODEC, Entry::result,
                        ByteBufCodecs.BYTE, Entry::availability,
                        Entry::new);
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
