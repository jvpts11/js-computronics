/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the nodes currently on the Mainframe's network, for the network overview screen.
 */
public record NetworkNodesPayload(String networkId, List<NetworkNodeInfo> nodes) implements CustomPacketPayload {

    public static final int MAX_NODES = 128;

    public static final CustomPacketPayload.Type<NetworkNodesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_nodes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkNodesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, NetworkNodesPayload::networkId,
                    NetworkNodeInfo.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NODES)), NetworkNodesPayload::nodes,
                    NetworkNodesPayload::new);

    @Override
    public CustomPacketPayload.Type<NetworkNodesPayload> type() {
        return TYPE;
    }
}
