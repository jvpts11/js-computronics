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
 * Client to server: serialize the selected Recipe ROM patterns as {@code .craft} files onto a
 * removable medium linked to the Crafting Computer.
 *
 * <p>Each index in {@code romIndices} corresponds to a zero-based position in the ROM list;
 * entries out of range are silently skipped. The file name is derived from the result item's
 * registry path (e.g. {@code diamond_sword.craft}).
 *
 * @param hostPos      the position of the Crafting Computer
 * @param mediaVolumeKey the {@code media:<readerPos>} key identifying the medium
 * @param romIndices   zero-based indices of the ROM patterns to download
 */
public record DownloadToMediaPayload(
        BlockPos hostPos,
        String mediaVolumeKey,
        List<Integer> romIndices) implements CustomPacketPayload {

    private static final int MAX_INDICES = 50;

    public static final CustomPacketPayload.Type<DownloadToMediaPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "download_to_media"));

    private record Wire(BlockPos pos, String key, List<Integer> indices) {
        static final StreamCodec<RegistryFriendlyByteBuf, Wire> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, Wire::pos,
                        ByteBufCodecs.stringUtf8(64), Wire::key,
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_INDICES)), Wire::indices,
                        Wire::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DownloadToMediaPayload> STREAM_CODEC =
            Wire.STREAM_CODEC.map(
                    w -> new DownloadToMediaPayload(w.pos(), w.key(), w.indices()),
                    p -> new Wire(p.hostPos(), p.mediaVolumeKey(), p.romIndices()));

    @Override
    public CustomPacketPayload.Type<DownloadToMediaPayload> type() {
        return TYPE;
    }
}
