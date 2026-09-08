/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the content of a file the Editor asked to open. {@code exists} is false when the
 * path had no readable user file (so the Editor opens it as a new, empty file with that name).
 */
public record FileContentPayload(String path, String content, boolean exists) implements CustomPacketPayload {

    public static final int MAX_CONTENT = 32768;

    public static final CustomPacketPayload.Type<FileContentPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "file_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FileContentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(160), FileContentPayload::path,
                    ByteBufCodecs.stringUtf8(MAX_CONTENT), FileContentPayload::content,
                    ByteBufCodecs.BOOL, FileContentPayload::exists,
                    FileContentPayload::new);

    @Override
    public CustomPacketPayload.Type<FileContentPayload> type() {
        return TYPE;
    }
}
