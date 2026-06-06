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
 * Client to server: the player opened the network overview from the Mainframe at {@code mainframePos} and wants the current list of connected nodes.
 */
public record RequestNetworkNodesPayload(BlockPos mainframePos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNetworkNodesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_network_nodes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestNetworkNodesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestNetworkNodesPayload::mainframePos,
                    RequestNetworkNodesPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestNetworkNodesPayload> type() {
        return TYPE;
    }
}
