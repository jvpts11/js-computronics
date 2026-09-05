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
 * Client to server: save the NMS editor's content as an {@code .iql} file on the Mainframe's system
 * disk. The file name is the base name without extension (the server appends {@code .iql}). On
 * completion the server replies with a refreshed {@link IqlFileListPayload}.
 *
 * @param hostPos  the position of the host computer (Mainframe or PC whose network has the Mainframe)
 * @param fileName the base file name, without extension (max {@value #MAX_NAME_LEN} chars)
 * @param content  the IQL script content to write (max {@value SaveScriptPayload#MAX_LEN} chars)
 */
public record SaveIqlFilePayload(BlockPos hostPos, String fileName,
                                 String content) implements CustomPacketPayload {

    /** Maximum base-name length (without the {@code .iql} extension). */
    public static final int MAX_NAME_LEN = 32;

    public static final CustomPacketPayload.Type<SaveIqlFilePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "save_iql_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveIqlFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SaveIqlFilePayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_NAME_LEN), SaveIqlFilePayload::fileName,
                    ByteBufCodecs.stringUtf8(SaveScriptPayload.MAX_LEN), SaveIqlFilePayload::content,
                    SaveIqlFilePayload::new);

    @Override
    public CustomPacketPayload.Type<SaveIqlFilePayload> type() {
        return TYPE;
    }
}
