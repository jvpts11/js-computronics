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
 * Client to server: open a named {@code .iql} file from the Mainframe's system disk and deliver its
 * content back to the NMS editor. The server reads the file and replies with an
 * {@link IqlFileContentPayload}.
 *
 * @param hostPos  the position of the host computer
 * @param fileName the full file path including the {@code .iql} extension
 */
public record OpenIqlFilePayload(BlockPos hostPos, String fileName) implements CustomPacketPayload {

    private static final int MAX_NAME = 40;

    public static final CustomPacketPayload.Type<OpenIqlFilePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "open_iql_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenIqlFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenIqlFilePayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_NAME), OpenIqlFilePayload::fileName,
                    OpenIqlFilePayload::new);

    @Override
    public CustomPacketPayload.Type<OpenIqlFilePayload> type() {
        return TYPE;
    }
}
