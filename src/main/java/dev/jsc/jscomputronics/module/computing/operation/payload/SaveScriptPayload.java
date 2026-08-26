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
 * Client to server: the Network Management Studio's editor script, saved to its Mainframe (addressed by the host position) so it survives closing and reopening the studio, and a world reload. The Studio sends this when it closes.
 */
public record SaveScriptPayload(BlockPos hostPos, String script) implements CustomPacketPayload {

    public static final int MAX_LEN = 8192;

    public static final CustomPacketPayload.Type<SaveScriptPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "save_script"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveScriptPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SaveScriptPayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_LEN), SaveScriptPayload::script,
                    SaveScriptPayload::new);

    @Override
    public CustomPacketPayload.Type<SaveScriptPayload> type() {
        return TYPE;
    }
}
