/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the result of a Network Management Studio statement — a status message (and whether it succeeded) plus the result-set rows for a read, which the Studio renders as a grid.
 */
public record SqlResultPayload(boolean ok, String message, List<Row> rows) implements CustomPacketPayload {

    public static final int MAX_ROWS = 256;

    public static final CustomPacketPayload.Type<SqlResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "sql_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SqlResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SqlResultPayload::ok,
                    ByteBufCodecs.stringUtf8(256), SqlResultPayload::message,
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROWS)), SqlResultPayload::rows,
                    SqlResultPayload::new);

    @Override
    public CustomPacketPayload.Type<SqlResultPayload> type() {
        return TYPE;
    }

    /** One result row: an item label and its quantity. */
    public record Row(String label, long quantity) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(128), Row::label,
                        ByteBufCodecs.VAR_LONG, Row::quantity,
                        Row::new);
    }
}
