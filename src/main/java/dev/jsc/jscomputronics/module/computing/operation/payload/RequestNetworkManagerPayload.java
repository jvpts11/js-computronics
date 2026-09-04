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

/**
 * Client to server: the desktop Network Manager on the computer at {@code hostPos} asks for a fresh
 * network snapshot. The server replies with a {@link NetworkManagerPayload}. It works from a Monitor
 * desktop hosted on a Mainframe (the node that holds the network index).
 */
public record RequestNetworkManagerPayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNetworkManagerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_network_manager"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestNetworkManagerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestNetworkManagerPayload::hostPos,
                    RequestNetworkManagerPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestNetworkManagerPayload> type() {
        return TYPE;
    }
}
