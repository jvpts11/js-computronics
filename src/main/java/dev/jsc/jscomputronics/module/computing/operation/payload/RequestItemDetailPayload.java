/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: Storage Insights asks for one item's full detail (where it is stored, what it makes,
 * and which buses filter it). The server replies with an {@link ItemDetailPayload}. Proximity + monitor
 * gated like the rest of the network views.
 */
public record RequestItemDetailPayload(BlockPos host, BlockPos monitorPos, ItemStack item)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestItemDetailPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_item_detail"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestItemDetailPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestItemDetailPayload::host,
                    BlockPos.STREAM_CODEC, RequestItemDetailPayload::monitorPos,
                    ItemStack.STREAM_CODEC, RequestItemDetailPayload::item,
                    RequestItemDetailPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestItemDetailPayload> type() {
        return TYPE;
    }
}
