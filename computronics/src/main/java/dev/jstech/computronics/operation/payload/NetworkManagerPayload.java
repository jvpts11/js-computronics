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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the snapshot the desktop Network Manager draws — the short network id and every
 * node on it. The Devices and Map tabs both read this node list; later tabs (Processes, Hardware, Log)
 * add their own data. Sent in reply to {@link RequestNetworkManagerPayload}.
 */
public record NetworkManagerPayload(BlockPos hostPos, String networkId, List<NetworkNodeInfo> nodes,
                                   Hardware hardware) implements CustomPacketPayload {

    public static final int MAX_NODES = 128;

    /**
     * Network-wide hardware totals the Hardware tab shows: the orchestration capacity in items/tick, the
     * number of parallel dispatch queues, the RAM buffer in items, and the total addressable storage in items
     * (the one unit every disk shares, whatever era it was made for).
     */
    public record Hardware(long capacity, int queues, long ramBuffer, long storageItems) {
        public static final Hardware EMPTY = new Hardware(0L, 0, 0L, 0L);

        public static final StreamCodec<RegistryFriendlyByteBuf, Hardware> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, Hardware::capacity,
                        ByteBufCodecs.VAR_INT, Hardware::queues,
                        ByteBufCodecs.VAR_LONG, Hardware::ramBuffer,
                        ByteBufCodecs.VAR_LONG, Hardware::storageItems,
                        Hardware::new);
    }

    public static final CustomPacketPayload.Type<NetworkManagerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_manager"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkManagerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NetworkManagerPayload::hostPos,
                    ByteBufCodecs.stringUtf8(48), NetworkManagerPayload::networkId,
                    NetworkNodeInfo.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NODES)), NetworkManagerPayload::nodes,
                    Hardware.STREAM_CODEC, NetworkManagerPayload::hardware,
                    NetworkManagerPayload::new);

    @Override
    public CustomPacketPayload.Type<NetworkManagerPayload> type() {
        return TYPE;
    }
}
