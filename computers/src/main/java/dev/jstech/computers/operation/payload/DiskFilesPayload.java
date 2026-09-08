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

import java.util.List;

/**
 * Server to client: the listing of directory {@code dir} on a computer's system disk, for the Files
 * app. Each {@link WireFile} carries the file path, its extension, its disk-space weight in
 * mB-equivalents, whether it is read-only (a {@code .dat} storage projection or an installer's files),
 * and, for a {@code .dat}, the item it projects and how many are stored.
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

        /** Whether this volume is a medium in a linked drive rather than the system disk. */
        public boolean removable() {
            return key.startsWith("media:");
        }
    }

    /**
     * One listed entry: path, extension (no dot), disk weight in mB-eq, the read-only flag, and the
     * {@code directory} flag; the client renders a directory as a folder that navigates on open. A
     * {@code .dat} entry also names the item it projects and how many of it are stored, so the
     * explorer can show the item itself instead of a file name.
     */
    public record WireFile(String path, String ext, long weight, boolean readOnly, boolean directory,
                           String itemId, long count) {

        /** The plain entry: no projected item. */
        public WireFile(final String path, final String ext, final long weight, final boolean readOnly,
                        final boolean directory) {
            this(path, ext, weight, readOnly, directory, "", 0L);
        }

        // Written by hand: composite() tops out at six pairs, and an entry now carries seven fields.
        public static final StreamCodec<RegistryFriendlyByteBuf, WireFile> STREAM_CODEC =
                StreamCodec.of((buf, f) -> {
                    buf.writeUtf(f.path(), 160);
                    buf.writeUtf(f.ext(), 16);
                    buf.writeVarLong(f.weight());
                    buf.writeBoolean(f.readOnly());
                    buf.writeBoolean(f.directory());
                    buf.writeUtf(f.itemId(), 96);
                    buf.writeVarLong(f.count());
                }, buf -> new WireFile(buf.readUtf(160), buf.readUtf(16), buf.readVarLong(), buf.readBoolean(),
                        buf.readBoolean(), buf.readUtf(96), buf.readVarLong()));

        /** Whether this entry projects a stored item. */
        public boolean projectsItem() {
            return !itemId.isEmpty();
        }
    }
}
