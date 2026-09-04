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

import java.util.List;

/**
 * Server to client: the channel bar of a KVM Switch — every machine the rack's switch can put on
 * this monitor, so the player picks which one the screen shows before the session starts.
 */
public record OpenKvmPayload(BlockPos rackPos, BlockPos monitorPos, int activeChannel,
                             List<Channel> channels) implements CustomPacketPayload {

    /** One addressable machine: its rack row, its name, and whether it is running. */
    public record Channel(int slot, String name, boolean running) {
    }

    public static final int MAX_CHANNELS = 8;

    public static final CustomPacketPayload.Type<OpenKvmPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "open_kvm"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Channel> CHANNEL_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Channel::slot,
                    ByteBufCodecs.STRING_UTF8, Channel::name,
                    ByteBufCodecs.BOOL, Channel::running,
                    Channel::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenKvmPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenKvmPayload::rackPos,
                    BlockPos.STREAM_CODEC, OpenKvmPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, OpenKvmPayload::activeChannel,
                    CHANNEL_CODEC.apply(ByteBufCodecs.list(MAX_CHANNELS)), OpenKvmPayload::channels,
                    OpenKvmPayload::new);

    @Override
    public CustomPacketPayload.Type<OpenKvmPayload> type() {
        return TYPE;
    }
}
