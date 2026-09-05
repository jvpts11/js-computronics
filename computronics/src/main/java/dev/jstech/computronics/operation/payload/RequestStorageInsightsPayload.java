/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the Storage Insights dashboard asks for a fresh view of the network's contents. The
 * server replies with a {@link StorageInsightsPayload}. Proximity + monitor-link gated like the Network
 * Interactor, since the reply exposes what the whole network holds.
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 */
public record RequestStorageInsightsPayload(BlockPos host, BlockPos monitorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestStorageInsightsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_storage_insights"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestStorageInsightsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestStorageInsightsPayload::host,
                    BlockPos.STREAM_CODEC, RequestStorageInsightsPayload::monitorPos,
                    RequestStorageInsightsPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestStorageInsightsPayload> type() {
        return TYPE;
    }
}
