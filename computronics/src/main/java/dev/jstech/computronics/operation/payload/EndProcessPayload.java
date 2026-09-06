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
 * Client to server: end the script process numbered {@code id} on the computer at {@code hostPos}.
 *
 * <p>A script is something the player started, so a task manager may end it, the same way one may
 * anywhere else. What the machine itself is made of is not on this route at all.
 */
public record EndProcessPayload(BlockPos hostPos, int id) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EndProcessPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "end_process"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EndProcessPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, EndProcessPayload::hostPos,
                    ByteBufCodecs.VAR_INT, EndProcessPayload::id,
                    EndProcessPayload::new);

    @Override
    public CustomPacketPayload.Type<EndProcessPayload> type() {
        return TYPE;
    }
}
