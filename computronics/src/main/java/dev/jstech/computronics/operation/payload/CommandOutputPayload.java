/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the styled output of one Command Prompt line, plus whether the console should be cleared before printing it (the {@code clear} command). Each line carries its text and a style ordinal the client maps to a colour.
 */
public record CommandOutputPayload(boolean clear, String prompt, List<WireLine> lines) implements CustomPacketPayload {

    public static final int MAX_LINES = 256;

    public static final CustomPacketPayload.Type<CommandOutputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "command_output"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CommandOutputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, CommandOutputPayload::clear,
                    ByteBufCodecs.stringUtf8(256), CommandOutputPayload::prompt,
                    WireLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), CommandOutputPayload::lines,
                    CommandOutputPayload::new);

    @Override
    public CustomPacketPayload.Type<CommandOutputPayload> type() {
        return TYPE;
    }

    /** One console line on the wire: its text and the ordinal of its {@code CliStyle}. */
    public record WireLine(String text, int style) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireLine> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(512), WireLine::text,
                        ByteBufCodecs.VAR_INT, WireLine::style,
                        WireLine::new);
    }
}
