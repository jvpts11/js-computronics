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
 * Client to server: the whole of folder {@code dir} on the system disk of the computer at
 * {@code hostPos}, every file whose name ends in {@code extension}.
 *
 * <p>An editor that reports on a program's neighbours has to read them all, and asking for twenty files
 * one at a time is twenty round trips for a question that is really one. The server answers with a
 * {@link FolderContentPayload}.
 */
public record RequestFolderContentPayload(BlockPos hostPos, String dir, String extension)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestFolderContentPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "request_folder_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFolderContentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestFolderContentPayload::hostPos,
                    ByteBufCodecs.stringUtf8(160), RequestFolderContentPayload::dir,
                    ByteBufCodecs.stringUtf8(16), RequestFolderContentPayload::extension,
                    RequestFolderContentPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestFolderContentPayload> type() {
        return TYPE;
    }
}
