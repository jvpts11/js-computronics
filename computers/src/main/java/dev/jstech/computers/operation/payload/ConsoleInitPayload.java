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

import java.util.List;

/**
 * Server to client when the Command Prompt opens: this computer's persisted command history (so the prompt remembers across closes and reloads) and the names + usages of every registered command (so the client can offer Tab completion and usage hints without knowing the server's command set).
 */
public record ConsoleInitPayload(net.minecraft.core.BlockPos hostPos, List<String> history,
                                 List<WireCommand> commands,
                                 List<String> devices) implements CustomPacketPayload {

    public static final int MAX_HISTORY = 128;
    public static final int MAX_COMMANDS = 128;
    /** One device name per drive the shell can see ({@code sda}, ...), for completing {@code /dev/} arguments. */
    public static final int MAX_DEVICES = 27;

    public static final CustomPacketPayload.Type<ConsoleInitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "console_init"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleInitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.core.BlockPos.STREAM_CODEC, ConsoleInitPayload::hostPos,
                    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(MAX_HISTORY)),
                    ConsoleInitPayload::history,
                    WireCommand.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_COMMANDS)),
                    ConsoleInitPayload::commands,
                    ByteBufCodecs.stringUtf8(16).apply(ByteBufCodecs.list(MAX_DEVICES)),
                    ConsoleInitPayload::devices,
                    ConsoleInitPayload::new);

    @Override
    public CustomPacketPayload.Type<ConsoleInitPayload> type() {
        return TYPE;
    }

    /**
     * One command's name and usage, for completion and hints. The usage cap is generous on purpose: a usage
     * line longer than the cap does not truncate, it fails to encode and disconnects the player opening the
     * console, for every console on every machine that offers the command.
     */
    public record WireCommand(String name, String usage) {

        public static final int MAX_USAGE = 256;

        public static final StreamCodec<RegistryFriendlyByteBuf, WireCommand> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(48), WireCommand::name,
                        ByteBufCodecs.stringUtf8(MAX_USAGE), WireCommand::usage,
                        WireCommand::new);
    }
}
