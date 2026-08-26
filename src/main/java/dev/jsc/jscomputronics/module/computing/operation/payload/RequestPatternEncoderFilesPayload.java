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
 * Client to server: request the list of {@code .craft} files on the removable medium currently
 * in the Pattern Encoder's media slot. The server replies with a {@link CraftFileListPayload}.
 *
 * @param encoderPos the position of the Pattern Encoder block entity
 */
public record RequestPatternEncoderFilesPayload(BlockPos encoderPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestPatternEncoderFilesPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "request_pattern_encoder_files"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPatternEncoderFilesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestPatternEncoderFilesPayload::encoderPos,
                    RequestPatternEncoderFilesPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestPatternEncoderFilesPayload> type() {
        return TYPE;
    }
}
