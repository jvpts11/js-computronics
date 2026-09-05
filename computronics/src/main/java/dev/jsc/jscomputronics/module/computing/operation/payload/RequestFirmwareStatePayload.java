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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the open firmware screen asks for the computer's boot entries and hardware summary. */
public record RequestFirmwareStatePayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestFirmwareStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_firmware_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFirmwareStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestFirmwareStatePayload::hostPos,
                    RequestFirmwareStatePayload::new);

    @Override
    public CustomPacketPayload.Type<RequestFirmwareStatePayload> type() {
        return TYPE;
    }
}
