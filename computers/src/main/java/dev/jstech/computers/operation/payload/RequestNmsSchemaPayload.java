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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the Network Management Studio for the host at {@code hostPos} opened and wants a snapshot of the live network for its Object Explorer. The server replies with an {@link NmsSchemaPayload}.
 */
public record RequestNmsSchemaPayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNmsSchemaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_nms_schema"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestNmsSchemaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestNmsSchemaPayload::hostPos,
                    RequestNmsSchemaPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestNmsSchemaPayload> type() {
        return TYPE;
    }
}
