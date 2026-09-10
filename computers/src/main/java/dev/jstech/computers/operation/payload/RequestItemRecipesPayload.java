/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import dev.jstech.computers.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the Network Interactor's details panel asks what makes {@code key} on this network and
 * what uses it, for the item it is showing. Answered with an {@link ItemRecipesPayload}.
 */
public record RequestItemRecipesPayload(BlockPos hostPos, BlockPos monitorPos, StorageKey key)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestItemRecipesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_item_recipes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestItemRecipesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestItemRecipesPayload::hostPos,
                    BlockPos.STREAM_CODEC, RequestItemRecipesPayload::monitorPos,
                    StorageKey.STREAM_CODEC, RequestItemRecipesPayload::key,
                    RequestItemRecipesPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestItemRecipesPayload> type() {
        return TYPE;
    }
}
