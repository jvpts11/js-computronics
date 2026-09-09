/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: run the compiled program at {@code path} on the computer at {@code hostPos}, the
 * way a double click on one in the explorer does. What the program prints comes back to the terminal
 * window whose shell {@code session} asked, or to every terminal when no window did.
 */
public record RunProgramPayload(BlockPos hostPos, String path, int session) implements CustomPacketPayload {

    /** As long a path as the filesystem itself allows. */
    public static final int MAX_PATH = 256;

    /** A run asked for by something that is not a terminal window. */
    public RunProgramPayload(final BlockPos hostPos, final String path) {
        this(hostPos, path, 0);
    }

    public static final CustomPacketPayload.Type<RunProgramPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "run_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunProgramPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RunProgramPayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_PATH), RunProgramPayload::path,
                    ByteBufCodecs.VAR_INT, RunProgramPayload::session,
                    RunProgramPayload::new);

    @Override
    public CustomPacketPayload.Type<RunProgramPayload> type() {
        return TYPE;
    }
}
