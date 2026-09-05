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
 * Client to server: the desktop Editor is saving a file {@code path} with {@code content} to the system
 * disk of the computer at {@code hostPos}. The server writes it via the filesystem and replies with a
 * {@link FileSavedPayload}.
 */
public record SaveFilePayload(BlockPos hostPos, String path, String content) implements CustomPacketPayload {

    public static final int MAX_CONTENT = 32768;

    public static final CustomPacketPayload.Type<SaveFilePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "save_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SaveFilePayload::hostPos,
                    ByteBufCodecs.stringUtf8(160), SaveFilePayload::path,
                    ByteBufCodecs.stringUtf8(MAX_CONTENT), SaveFilePayload::content,
                    SaveFilePayload::new);

    @Override
    public CustomPacketPayload.Type<SaveFilePayload> type() {
        return TYPE;
    }
}
