/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the Storage Insights dashboard. Carries the network-wide totals (item count, distinct
 * types, server count), the biggest types for the bar chart ({@link #topItems}), the smallest non-empty
 * types as low-stock candidates the client filters against a threshold ({@link #lowItems}), and how much
 * each server holds ({@link #servers}). Sent in reply to {@link RequestStorageInsightsPayload}.
 */
public record StorageInsightsPayload(long totalItems, int typeCount, int serverCount,
                                     List<NetworkItemEntry> topItems, List<NetworkItemEntry> lowItems,
                                     List<NetworkItemEntry.StorageShare> servers) implements CustomPacketPayload {

    public static final int MAX_TOP = 16;
    public static final int MAX_LOW = 24;
    public static final int MAX_SERVERS = 32;

    public static final CustomPacketPayload.Type<StorageInsightsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "storage_insights"));

    // Hand-written: more components than a composite overload takes.
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageInsightsPayload> STREAM_CODEC =
            StreamCodec.of(StorageInsightsPayload::encode, StorageInsightsPayload::decode);

    private static void encode(final RegistryFriendlyByteBuf buf, final StorageInsightsPayload p) {
        buf.writeVarLong(p.totalItems);
        buf.writeVarInt(p.typeCount);
        buf.writeVarInt(p.serverCount);
        NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TOP)).encode(buf, p.topItems);
        NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LOW)).encode(buf, p.lowItems);
        NetworkItemEntry.StorageShare.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SERVERS)).encode(buf, p.servers);
    }

    private static StorageInsightsPayload decode(final RegistryFriendlyByteBuf buf) {
        final long totalItems = buf.readVarLong();
        final int typeCount = buf.readVarInt();
        final int serverCount = buf.readVarInt();
        final List<NetworkItemEntry> top =
                NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TOP)).decode(buf);
        final List<NetworkItemEntry> low =
                NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LOW)).decode(buf);
        final List<NetworkItemEntry.StorageShare> servers =
                NetworkItemEntry.StorageShare.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SERVERS)).decode(buf);
        return new StorageInsightsPayload(totalItems, typeCount, serverCount, top, low, servers);
    }

    @Override
    public CustomPacketPayload.Type<StorageInsightsPayload> type() {
        return TYPE;
    }
}
