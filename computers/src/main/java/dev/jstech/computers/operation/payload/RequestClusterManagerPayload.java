/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the Cluster Manager asks for its state. The selection says which cluster the
 * detail pane is showing, so the answer carries that cluster's nodes and queue.
 *
 * @param hostPos  the Cluster Management Computer
 * @param selKind  the selected cluster kind (0 supercomputer, 1 datacenter, 2 AI)
 * @param selIndex the selected cluster's index within its kind, or -1
 */
public record RequestClusterManagerPayload(BlockPos hostPos, int selKind, int selIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestClusterManagerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_cluster_manager"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestClusterManagerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestClusterManagerPayload::hostPos,
                    ByteBufCodecs.VAR_INT, RequestClusterManagerPayload::selKind,
                    ByteBufCodecs.VAR_INT, RequestClusterManagerPayload::selIndex,
                    RequestClusterManagerPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestClusterManagerPayload> type() {
        return TYPE;
    }
}
