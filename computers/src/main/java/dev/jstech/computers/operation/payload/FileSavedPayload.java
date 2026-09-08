/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the result of an Editor save: whether it succeeded and a short status message to
 * show in the Editor's status line.
 */
public record FileSavedPayload(boolean ok, String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FileSavedPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "file_saved"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FileSavedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, FileSavedPayload::ok,
                    ByteBufCodecs.stringUtf8(160), FileSavedPayload::message,
                    FileSavedPayload::new);

    @Override
    public CustomPacketPayload.Type<FileSavedPayload> type() {
        return TYPE;
    }
}
