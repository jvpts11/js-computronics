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
 * Server to client: the listing of directory {@code dir} on a computer's system disk, for the Files
 * app. Each {@link WireFile} carries the file path, its extension, its disk-space weight in
 * mB-equivalents, and whether it is read-only (a {@code .dat} storage projection).
 */
public record DiskFilesPayload(String dir, List<WireFile> files,
                               List<WireVolume> volumes) implements CustomPacketPayload {

    public static final int MAX_FILES = 512;
    public static final int MAX_VOLUMES = 32;

    public static final CustomPacketPayload.Type<DiskFilesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "disk_files"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskFilesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(128), DiskFilesPayload::dir,
                    WireFile.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES)), DiskFilesPayload::files,
                    WireVolume.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_VOLUMES)), DiskFilesPayload::volumes,
                    DiskFilesPayload::new);

    @Override
    public CustomPacketPayload.Type<DiskFilesPayload> type() {
        return TYPE;
    }

    /**
     * One mountable volume for the explorer's drive tree: a {@code key} that addresses it ({@code ""}
     * for the system disk, {@code "media:<readerPos>"} for a removable drive) and its display label.
     */
    public record WireVolume(String key, String label) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireVolume> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(64), WireVolume::key,
                        ByteBufCodecs.stringUtf8(64), WireVolume::label,
                        WireVolume::new);
    }

    /**
     * One listed entry: path, extension (no dot), disk weight in mB-eq, the read-only flag, and the
     * directory flag. A directory entry has an empty extension, zero weight, and
     * {@code directory == true}; the client renders it as a folder that navigates on open.
     */
    public record WireFile(String path, String ext, long weight, boolean readOnly, boolean directory) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireFile> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(160), WireFile::path,
                        ByteBufCodecs.stringUtf8(16), WireFile::ext,
                        ByteBufCodecs.VAR_LONG, WireFile::weight,
                        ByteBufCodecs.BOOL, WireFile::readOnly,
                        ByteBufCodecs.BOOL, WireFile::directory,
                        WireFile::new);
    }
}
