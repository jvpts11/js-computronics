/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the name typed in a Server's assembly GUI.
 */
public record RenameServerPayload(String name) implements CustomPacketPayload {

    public static final int MAX_LEN = 32;

    public static final CustomPacketPayload.Type<RenameServerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "rename_server"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameServerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_LEN), RenameServerPayload::name,
                    RenameServerPayload::new);

    @Override
    public CustomPacketPayload.Type<RenameServerPayload> type() {
        return TYPE;
    }
}
