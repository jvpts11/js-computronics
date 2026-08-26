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
 * Client to server: drag-and-drop is moving the file or directory {@code srcPath} into the
 * directory {@code destDir} on the system disk of the computer at {@code hostPos}. The item keeps
 * its name; a directory move re-keys everything nested under it. {@code .dat} projections cannot be
 * moved.
 */
public record MoveFilePayload(BlockPos hostPos, String srcPath, String destDir)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MoveFilePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "move_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, MoveFilePayload::hostPos,
                    ByteBufCodecs.stringUtf8(160), MoveFilePayload::srcPath,
                    ByteBufCodecs.stringUtf8(160), MoveFilePayload::destDir,
                    MoveFilePayload::new);

    @Override
    public CustomPacketPayload.Type<MoveFilePayload> type() {
        return TYPE;
    }
}
