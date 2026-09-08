/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: every file of one kind in a folder, with what is in each.
 *
 * <p>What an editor needs to say which of a machine's programs stopped compiling when a shared one
 * changed. Both what it carries and how many files it will carry are capped, because a payload that
 * grows with a player's disk is a payload that eventually will not encode: a drive can hold thousands
 * of files, and past this the editor reports on what it could read and says so.
 */
public record FolderContentPayload(String dir, List<WireFile> files) implements CustomPacketPayload {

    /** How many files one reply carries. */
    public static final int MAX_FILES = 64;
    /** How long one of them may be. */
    public static final int MAX_TEXT = 16_384;

    /** One file: where it is, and what is in it. */
    public record WireFile(String path, String text) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireFile> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(160), WireFile::path,
                        ByteBufCodecs.stringUtf8(MAX_TEXT), WireFile::text,
                        WireFile::new);
    }

    public static final CustomPacketPayload.Type<FolderContentPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "folder_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FolderContentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(160), FolderContentPayload::dir,
                    WireFile.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES)), FolderContentPayload::files,
                    FolderContentPayload::new);

    public FolderContentPayload {
        files = List.copyOf(files);
    }

    @Override
    public CustomPacketPayload.Type<FolderContentPayload> type() {
        return TYPE;
    }
}
