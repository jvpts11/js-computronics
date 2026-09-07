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
 * Client to server: the Network Interactor asks for the network's computers (Mainframe, Servers, PCs)
 * to populate the request popup's advanced mode: the PULL FROM source list and the SEND TO destination
 * cycle. The server replies with a {@link NetworkServersPayload}.
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 */
public record RequestNiServersPayload(BlockPos host, BlockPos monitorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNiServersPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_ni_servers"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestNiServersPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestNiServersPayload::host,
                    BlockPos.STREAM_CODEC, RequestNiServersPayload::monitorPos,
                    RequestNiServersPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestNiServersPayload> type() {
        return TYPE;
    }
}
