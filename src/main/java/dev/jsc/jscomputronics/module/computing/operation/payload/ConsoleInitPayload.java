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

import java.util.List;

/**
 * Server to client when the Command Prompt opens: this computer's persisted command history (so the prompt remembers across closes and reloads) and the names + usages of every registered command (so the client can offer Tab completion and usage hints without knowing the server's command set).
 */
public record ConsoleInitPayload(List<String> history, List<WireCommand> commands) implements CustomPacketPayload {

    public static final int MAX_HISTORY = 128;
    public static final int MAX_COMMANDS = 128;

    public static final CustomPacketPayload.Type<ConsoleInitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "console_init"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleInitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(MAX_HISTORY)),
                    ConsoleInitPayload::history,
                    WireCommand.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_COMMANDS)),
                    ConsoleInitPayload::commands,
                    ConsoleInitPayload::new);

    @Override
    public CustomPacketPayload.Type<ConsoleInitPayload> type() {
        return TYPE;
    }

    /** One command's name and usage, for completion and hints. */
    public record WireCommand(String name, String usage) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireCommand> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(48), WireCommand::name,
                        ByteBufCodecs.stringUtf8(96), WireCommand::usage,
                        WireCommand::new);
    }
}
