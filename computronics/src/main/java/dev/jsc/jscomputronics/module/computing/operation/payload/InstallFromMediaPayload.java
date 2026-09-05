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
 * Client to server: install the program carried by the PROGRAM_INSTALL medium in the drive at
 * {@code readerPos} onto the computer at {@code hostPos}. The server verifies the drive is linked to
 * the computer, reads the program id, and adds it to the computer's installed set.
 */
public record InstallFromMediaPayload(BlockPos hostPos, long readerPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InstallFromMediaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "install_from_media"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InstallFromMediaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, InstallFromMediaPayload::hostPos,
                    ByteBufCodecs.VAR_LONG, InstallFromMediaPayload::readerPos,
                    InstallFromMediaPayload::new);

    @Override
    public CustomPacketPayload.Type<InstallFromMediaPayload> type() {
        return TYPE;
    }
}
