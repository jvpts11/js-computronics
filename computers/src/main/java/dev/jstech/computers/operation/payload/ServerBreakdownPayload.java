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
 * Server to client: which Servers hold the item the player clicked in the Network tab, and how much each has, for the source-server picker in the request popup.
 */
public record ServerBreakdownPayload(List<ServerHolding> servers) implements CustomPacketPayload {

    public static final int MAX = 64;

    /**
     * One Server's holding of a single item.
     */
    public record ServerHolding(String key, String label, long count) {

        public static final StreamCodec<RegistryFriendlyByteBuf, ServerHolding> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ServerHolding::key,
                        ByteBufCodecs.STRING_UTF8, ServerHolding::label,
                        ByteBufCodecs.VAR_LONG, ServerHolding::count,
                        ServerHolding::new);
    }

    public static final CustomPacketPayload.Type<ServerBreakdownPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "server_breakdown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerBreakdownPayload> STREAM_CODEC =
            ServerHolding.STREAM_CODEC.apply(ByteBufCodecs.list(MAX))
                    .map(ServerBreakdownPayload::new, ServerBreakdownPayload::servers);

    @Override
    public CustomPacketPayload.Type<ServerBreakdownPayload> type() {
        return TYPE;
    }
}
