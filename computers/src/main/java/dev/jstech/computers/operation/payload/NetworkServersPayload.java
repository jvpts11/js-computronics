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
 * Server to client: every Server on the network with its display name and free space, for the destination picker in the request popup's advanced mode (where a MOVE can target a specific Server).
 */
public record NetworkServersPayload(List<ServerEntry> servers) implements CustomPacketPayload {

    public static final int MAX = 64;

    /**
     * One Server on the network.
     */
    public record ServerEntry(String key, String name, long free) {

        public static final StreamCodec<RegistryFriendlyByteBuf, ServerEntry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ServerEntry::key,
                        ByteBufCodecs.STRING_UTF8, ServerEntry::name,
                        ByteBufCodecs.VAR_LONG, ServerEntry::free,
                        ServerEntry::new);
    }

    public static final CustomPacketPayload.Type<NetworkServersPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_servers"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkServersPayload> STREAM_CODEC =
            ServerEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX))
                    .map(NetworkServersPayload::new, NetworkServersPayload::servers);

    @Override
    public CustomPacketPayload.Type<NetworkServersPayload> type() {
        return TYPE;
    }
}
