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
 * Client to server: remove an installed program from the computer (the Settings app's Uninstall button).
 * Runs through the same removal path as the shells' package removal, so Mainframe services turn their
 * agent off and a removed desktop environment drops the computer back to the TTY on its next boot.
 */
public record UninstallProgramPayload(BlockPos hostPos, String programId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UninstallProgramPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "uninstall_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UninstallProgramPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, UninstallProgramPayload::hostPos,
                    ByteBufCodecs.STRING_UTF8, UninstallProgramPayload::programId,
                    UninstallProgramPayload::new);

    @Override
    public CustomPacketPayload.Type<UninstallProgramPayload> type() {
        return TYPE;
    }
}
