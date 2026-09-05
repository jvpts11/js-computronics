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
 * Client to server: eject the medium in the drive at {@code readerPos}, which must be linked to the
 * computer at {@code hostPos}. The medium goes to the player's inventory, or onto the drive when the
 * inventory is full, the same as sneak-clicking the drive in the world.
 */
public record EjectMediaPayload(BlockPos hostPos, long readerPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EjectMediaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "eject_media"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EjectMediaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, EjectMediaPayload::hostPos,
                    ByteBufCodecs.VAR_LONG, EjectMediaPayload::readerPos,
                    EjectMediaPayload::new);

    @Override
    public CustomPacketPayload.Type<EjectMediaPayload> type() {
        return TYPE;
    }
}
