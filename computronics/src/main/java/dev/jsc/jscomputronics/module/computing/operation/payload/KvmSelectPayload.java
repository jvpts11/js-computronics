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
 * Client to server: the player picked a channel on the KVM Switch — put the machine in rack row
 * {@code slot} on the monitor and start its session.
 */
public record KvmSelectPayload(BlockPos rackPos, BlockPos monitorPos, int slot)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KvmSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "kvm_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KvmSelectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, KvmSelectPayload::rackPos,
                    BlockPos.STREAM_CODEC, KvmSelectPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, KvmSelectPayload::slot,
                    KvmSelectPayload::new);

    @Override
    public CustomPacketPayload.Type<KvmSelectPayload> type() {
        return TYPE;
    }
}
