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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the desktop wants the content of file {@code path} on the system disk of the
 * computer at {@code hostPos} (e.g. to open it in the Editor). The server replies with a
 * {@link FileContentPayload}.
 */
public record RequestFileContentPayload(BlockPos hostPos, String path) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestFileContentPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_file_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFileContentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestFileContentPayload::hostPos,
                    ByteBufCodecs.stringUtf8(160), RequestFileContentPayload::path,
                    RequestFileContentPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestFileContentPayload> type() {
        return TYPE;
    }
}
