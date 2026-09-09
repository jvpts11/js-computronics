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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A player asking a machine to stop the program it is setting up.
 *
 * <p>Nothing is installed by a cancelled setup: the install itself only happens on the last tick, so
 * stopping before it leaves the machine as it was.
 *
 * @param hostPos the machine
 */
public record CancelSetupPayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CancelSetupPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "cancel_setup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelSetupPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, CancelSetupPayload::hostPos, CancelSetupPayload::new);

    @Override
    public CustomPacketPayload.Type<CancelSetupPayload> type() {
        return TYPE;
    }
}
