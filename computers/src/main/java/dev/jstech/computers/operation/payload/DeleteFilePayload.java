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
 * Client to server: the Files explorer is deleting {@code path} from the system disk of the computer at
 * {@code hostPos}. The server removes the real file via the filesystem (never a {@code .dat} projection).
 */
public record DeleteFilePayload(BlockPos hostPos, String path) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteFilePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "delete_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DeleteFilePayload::hostPos,
                    ByteBufCodecs.stringUtf8(160), DeleteFilePayload::path,
                    DeleteFilePayload::new);

    @Override
    public CustomPacketPayload.Type<DeleteFilePayload> type() {
        return TYPE;
    }
}
