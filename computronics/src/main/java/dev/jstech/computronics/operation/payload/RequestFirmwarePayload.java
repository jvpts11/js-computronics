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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: restart into the firmware setup even though an OS is installed (from the desktop's
 * Settings or the shell's {@code reboot --firmware}). The server closes the current screen and opens the
 * era-correct firmware on the given monitor.
 */
public record RequestFirmwarePayload(BlockPos hostPos, BlockPos monitorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestFirmwarePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_firmware"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFirmwarePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestFirmwarePayload::hostPos,
                    BlockPos.STREAM_CODEC, RequestFirmwarePayload::monitorPos,
                    RequestFirmwarePayload::new);

    @Override
    public CustomPacketPayload.Type<RequestFirmwarePayload> type() {
        return TYPE;
    }
}
