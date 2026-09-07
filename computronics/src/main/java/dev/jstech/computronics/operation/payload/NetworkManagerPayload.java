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
 * Server to client: the snapshot the desktop Network Manager draws: the short network id, every node on
 * it, the hardware totals and the last hour's Operation statistics. The Devices and Map tabs read the node
 * list, Hardware the totals, Stats the statistics; Processes and Log arrive by their own payloads. Sent in
 * reply to {@link RequestNetworkManagerPayload}.
 */
public record NetworkManagerPayload(BlockPos hostPos, String networkId, List<NetworkNodeInfo> nodes,
                                   Hardware hardware, Statistics statistics) implements CustomPacketPayload {

    public static final int MAX_NODES = 128;
    public static final int MAX_STAT_TYPES = 16;

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

    /**
     * One Operation type's last hour: how many settled, the share that fell short (whole percent), the mean
     * ticks waited before running and the mean ticks run, and what the type moved.
     */
    public record TypeStat(byte type, int count, int shortfallPercent, int averageWait, int averageRun, long moved) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TypeStat> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BYTE, TypeStat::type,
                        ByteBufCodecs.VAR_INT, TypeStat::count,
                        ByteBufCodecs.VAR_INT, TypeStat::shortfallPercent,
                        ByteBufCodecs.VAR_INT, TypeStat::averageWait,
                        ByteBufCodecs.VAR_INT, TypeStat::averageRun,
                        ByteBufCodecs.VAR_LONG, TypeStat::moved,
                        TypeStat::new);
    }

    /** The Stats tab: the per-type rows, the day's peak of Operations in flight, and the hour's moved total. */
    public record Statistics(List<TypeStat> types, int peakConcurrent, long movedLastHour) {
        public static final Statistics EMPTY = new Statistics(List.of(), 0, 0L);

        public static final StreamCodec<RegistryFriendlyByteBuf, Statistics> STREAM_CODEC =
                StreamCodec.composite(
                        TypeStat.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STAT_TYPES)), Statistics::types,
                        ByteBufCodecs.VAR_INT, Statistics::peakConcurrent,
                        ByteBufCodecs.VAR_LONG, Statistics::movedLastHour,
                        Statistics::new);
    }

    public static final CustomPacketPayload.Type<NetworkManagerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_manager"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkManagerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NetworkManagerPayload::hostPos,
                    ByteBufCodecs.stringUtf8(48), NetworkManagerPayload::networkId,
                    NetworkNodeInfo.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NODES)), NetworkManagerPayload::nodes,
                    Hardware.STREAM_CODEC, NetworkManagerPayload::hardware,
                    Statistics.STREAM_CODEC, NetworkManagerPayload::statistics,
                    NetworkManagerPayload::new);

    @Override
    public CustomPacketPayload.Type<NetworkManagerPayload> type() {
        return TYPE;
    }
}
