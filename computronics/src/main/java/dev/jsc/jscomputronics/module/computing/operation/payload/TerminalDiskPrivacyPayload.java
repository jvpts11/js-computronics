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
 * Client to server: the player dragged the Storage tab's privacy slider for {@code diskIndex} to {@code permille} (0..1000), setting how much of that disk's storage is public to the network.
 */
public record TerminalDiskPrivacyPayload(BlockPos monitorPos, BlockPos hostPos, int diskIndex, int permille)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TerminalDiskPrivacyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_disk_privacy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalDiskPrivacyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalDiskPrivacyPayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalDiskPrivacyPayload::hostPos,
                    ByteBufCodecs.VAR_INT, TerminalDiskPrivacyPayload::diskIndex,
                    ByteBufCodecs.VAR_INT, TerminalDiskPrivacyPayload::permille,
                    TerminalDiskPrivacyPayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalDiskPrivacyPayload> type() {
        return TYPE;
    }
}
