/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: rename {@code oldPath} to {@code newPath} on the system disk of the computer at
 * {@code hostPos}. The server reads the old file's content, writes it under the new name, and deletes
 * the old one (never a {@code .dat} projection).
 */
public record RenameFilePayload(BlockPos hostPos, String oldPath, String newPath)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameFilePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "rename_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RenameFilePayload::hostPos,
                    ByteBufCodecs.stringUtf8(160), RenameFilePayload::oldPath,
                    ByteBufCodecs.stringUtf8(160), RenameFilePayload::newPath,
                    RenameFilePayload::new);

    @Override
    public CustomPacketPayload.Type<RenameFilePayload> type() {
        return TYPE;
    }
}
