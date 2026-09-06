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

import java.util.List;

/**
 * Client to server: load one or more {@code .craft} files from a removable medium into the
 * Crafting Computer's Recipe ROM.
 *
 * <p>When {@code allMissing} is true the server loads every {@code .craft} file on the medium
 * that does not already have a matching pattern in the ROM; {@code fileNames} is ignored in that
 * case. Otherwise the server loads exactly the files listed in {@code fileNames}.
 *
 * @param hostPos      the position of the Crafting Computer
 * @param mediaVolumeKey the {@code media:<readerPos>} key identifying the medium
 * @param fileNames    the {@code .craft} file names to load (ignored when {@code allMissing})
 * @param allMissing   when true, load all files not already in the ROM instead of the explicit list
 */
public record LoadFromMediaPayload(
        BlockPos hostPos,
        String mediaVolumeKey,
        List<String> fileNames,
        boolean allMissing) implements CustomPacketPayload {

    private static final int MAX_FILES = 64;

    public static final CustomPacketPayload.Type<LoadFromMediaPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "load_from_media"));

    private record FileName(String value) {
        static final StreamCodec<RegistryFriendlyByteBuf, FileName> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(dev.jstech.computronics.os.fs.FsPaths.MAX_NAME_LENGTH),
                        FileName::value,
                        FileName::new);
    }

    private record Wire(BlockPos pos, String key, List<FileName> files, boolean all) {
        static final StreamCodec<RegistryFriendlyByteBuf, Wire> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, Wire::pos,
                        ByteBufCodecs.stringUtf8(64), Wire::key,
                        FileName.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES)), Wire::files,
                        ByteBufCodecs.BOOL, Wire::all,
                        Wire::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadFromMediaPayload> STREAM_CODEC =
            Wire.STREAM_CODEC.map(
                    w -> new LoadFromMediaPayload(
                            w.pos(), w.key(),
                            w.files().stream().map(FileName::value).toList(),
                            w.all()),
                    p -> new Wire(
                            p.hostPos(), p.mediaVolumeKey(),
                            p.fileNames().stream().map(FileName::new).toList(),
                            p.allMissing()));

    @Override
    public CustomPacketPayload.Type<LoadFromMediaPayload> type() {
        return TYPE;
    }
}
