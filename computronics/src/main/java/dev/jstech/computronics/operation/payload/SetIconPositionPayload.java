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

/**
 * Client to server: pin the desktop icon {@code iconKey} ({@code app:<label>} for a program launcher,
 * {@code file:<name>} for a desktop file or folder) to the desktop grid {@code cell}. The cell packs the
 * grid column in its high 16 bits and the row in the low 16 bits, so an icon stays put across monitor sizes
 * (snap-to-grid). Persisted on the computer so the layout survives a reload.
 */
public record SetIconPositionPayload(BlockPos hostPos, String iconKey, int cell)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetIconPositionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "set_icon_position"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetIconPositionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetIconPositionPayload::hostPos,
                    ByteBufCodecs.stringUtf8(80), SetIconPositionPayload::iconKey,
                    ByteBufCodecs.INT, SetIconPositionPayload::cell,
                    SetIconPositionPayload::new);

    @Override
    public CustomPacketPayload.Type<SetIconPositionPayload> type() {
        return TYPE;
    }
}
