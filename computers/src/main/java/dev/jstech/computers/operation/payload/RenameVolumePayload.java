/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: relabel a disk or media volume from the explorer's drive tree.
 *
 * @param host      the computer block whose volumes are being addressed
 * @param volumeKey {@code ""} for the system disk, or {@code "media:<readerPos>"} for a removable drive
 * @param label     the new label (blank clears it, restoring the default name)
 */
public record RenameVolumePayload(BlockPos host, String volumeKey, String label) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameVolumePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "rename_volume"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameVolumePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RenameVolumePayload::host,
                    ByteBufCodecs.stringUtf8(48), RenameVolumePayload::volumeKey,
                    ByteBufCodecs.stringUtf8(64), RenameVolumePayload::label,
                    RenameVolumePayload::new);

    @Override
    public CustomPacketPayload.Type<RenameVolumePayload> type() {
        return TYPE;
    }
}
