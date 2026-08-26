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
 * Client to server: the name typed in a bus part's configuration GUI, addressed by the cable position and the mounted face. The name lets a query reference the bus (FROM/TO &lt;name&gt;).
 */
public record SetBusNamePayload(BlockPos cablePos, int face, String name) implements CustomPacketPayload {

    public static final int MAX_LEN = 32;

    public static final CustomPacketPayload.Type<SetBusNamePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "set_bus_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetBusNamePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetBusNamePayload::cablePos,
                    ByteBufCodecs.VAR_INT, SetBusNamePayload::face,
                    ByteBufCodecs.stringUtf8(MAX_LEN), SetBusNamePayload::name,
                    SetBusNamePayload::new);

    @Override
    public CustomPacketPayload.Type<SetBusNamePayload> type() {
        return TYPE;
    }
}
