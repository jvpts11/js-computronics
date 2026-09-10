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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: what the network makes {@code key} with and what it uses {@code key} in, for the Network
 * Interactor's details panel. {@code madeBy} lists every recipe whose result is the key, one line each (its
 * name, kind and machines); {@code usedIn} names the results of the patterns that consume it.
 */
public record ItemRecipesPayload(StorageKey key, List<String> madeBy, List<String> usedIn)
        implements CustomPacketPayload {

    public static final int MAX_LINES = 16;
    public static final int MAX_TEXT = 96;

    public static final CustomPacketPayload.Type<ItemRecipesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "item_recipes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemRecipesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    StorageKey.STREAM_CODEC, ItemRecipesPayload::key,
                    ByteBufCodecs.stringUtf8(MAX_TEXT).apply(ByteBufCodecs.list(MAX_LINES)), ItemRecipesPayload::madeBy,
                    ByteBufCodecs.stringUtf8(MAX_TEXT).apply(ByteBufCodecs.list(MAX_LINES)), ItemRecipesPayload::usedIn,
                    ItemRecipesPayload::new);

    @Override
    public CustomPacketPayload.Type<ItemRecipesPayload> type() {
        return TYPE;
    }
}
