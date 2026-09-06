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
 * Client to server: run the compiled program at {@code path} on the computer at {@code hostPos}.
 *
 * <p>This is what opening one in the file explorer does. The path is the one the explorer knows, from
 * the root of a disk, rather than one relative to wherever a shell happens to be standing.
 */
public record RunProgramPayload(BlockPos hostPos, String path) implements CustomPacketPayload {

    /** As long a path as the filesystem itself allows. */
    public static final int MAX_PATH = 256;

    public static final CustomPacketPayload.Type<RunProgramPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "run_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunProgramPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RunProgramPayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_PATH), RunProgramPayload::path,
                    RunProgramPayload::new);

    @Override
    public CustomPacketPayload.Type<RunProgramPayload> type() {
        return TYPE;
    }
}
