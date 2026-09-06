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
 * Client to server: flip the power switch of the bay whose unit tops at {@code slot} in the Server
 * Rack at {@code rackPos} — the PWR button on the rack GUI's bay row.
 */
public record RackBayPowerPayload(BlockPos rackPos, int slot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RackBayPowerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "rack_bay_power"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RackBayPowerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RackBayPowerPayload::rackPos,
                    ByteBufCodecs.VAR_INT, RackBayPowerPayload::slot,
                    RackBayPowerPayload::new);

    @Override
    public CustomPacketPayload.Type<RackBayPowerPayload> type() {
        return TYPE;
    }
}
