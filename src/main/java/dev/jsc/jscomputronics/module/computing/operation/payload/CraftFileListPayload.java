/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the list of {@code .craft} file names present on a medium or disk. Sent in
 * response to a {@link RequestPatternEncoderFilesPayload} so the Pattern Encoder screen can show
 * what is already written on the inserted medium.
 *
 * <p>Each entry is the full file path (base name + {@code .craft} extension).
 *
 * @param files the list of {@code .craft} file paths (may be empty)
 */
public record CraftFileListPayload(List<String> files) implements CustomPacketPayload {

    /** Maximum number of file entries sent in one payload. */
    public static final int MAX_FILES = 64;

    /**
     * Maximum characters per file name: the filesystem's own limit, since a player can rename a {@code .craft}
     * to anything the explorer accepts and a name over the wire cap would disconnect them on the next listing.
     */
    private static final int MAX_NAME = dev.jsc.jscomputronics.module.computing.os.fs.FsPaths.MAX_NAME_LENGTH;

    public static final CustomPacketPayload.Type<CraftFileListPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "craft_file_list"));

    /** A single-field record whose {@code STREAM_CODEC} is already typed to {@link RegistryFriendlyByteBuf}. */
    private record FileName(String value) {
        static final StreamCodec<RegistryFriendlyByteBuf, FileName> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_NAME), FileName::value,
                        FileName::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftFileListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    FileName.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES)),
                    payload -> payload.files().stream().map(FileName::new).toList(),
                    names -> new CraftFileListPayload(names.stream().map(FileName::value).toList()));

    @Override
    public CustomPacketPayload.Type<CraftFileListPayload> type() {
        return TYPE;
    }
}
