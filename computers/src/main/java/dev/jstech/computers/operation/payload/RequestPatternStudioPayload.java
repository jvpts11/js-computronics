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
 * Client to server: the Pattern Studio window on the monitor at {@code monitorPos} wants the current state of
 * the workbench on the computer at {@code host}. The server replies with a {@link PatternStudioStatePayload}.
 */
public record RequestPatternStudioPayload(BlockPos host, BlockPos monitorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestPatternStudioPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_pattern_studio"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPatternStudioPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestPatternStudioPayload::host,
                    BlockPos.STREAM_CODEC, RequestPatternStudioPayload::monitorPos,
                    RequestPatternStudioPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestPatternStudioPayload> type() {
        return TYPE;
    }
}
