/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network.payload;

import dev.jstech.core.JsCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A minimal bidirectional payload carrying a single timestamp, used to measure round-trip latency and as the canonical payload template.
 */
public record PingPayload(PingData data)
        implements CustomPacketPayload, JscPayload {

    public PingPayload(final long timestamp) {
        this(new PingData(timestamp));
    }

    public long timestamp() {
        return data.timestamp();
    }

    public static final String ID = "ping";

    public static final CustomPacketPayload.Type<PingPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(JsCore.MODID, ID));

    public static final StreamCodec<FriendlyByteBuf, PingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, PingPayload::timestamp,
                    PingPayload::new);

    @Override
    public CustomPacketPayload.Type<PingPayload> type() {
        return TYPE;
    }

    @Override
    public String payloadId() {
        return ID;
    }
}