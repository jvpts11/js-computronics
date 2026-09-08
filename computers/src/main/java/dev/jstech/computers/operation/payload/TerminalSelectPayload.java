/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import dev.jstech.computers.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client to server: a Monitor terminal asked to SELECT {@code quantity} of {@code key}'s data type (item OR fluid, with components) from the chosen source Servers to a chosen destination: the computer's local storage, or another computer on the network (a MOVE).
 */
public record TerminalSelectPayload(BlockPos monitorPos, BlockPos hostPos, StorageKey key, long quantity,
                                    List<String> serverKeys, int destKind, String destServer)
        implements CustomPacketPayload {

    public static final int MAX_SERVERS = 64;

    public static final int DEST_AUTO = 0;
    public static final int DEST_INVENTORY = 1;
    public static final int DEST_LOCAL = 2;
    public static final int DEST_SERVER = 3;

    public static final CustomPacketPayload.Type<TerminalSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_select"));

    // Built by hand because the record has more components than StreamCodec.composite carries.
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSelectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        BlockPos.STREAM_CODEC.encode(buf, p.monitorPos());
                        BlockPos.STREAM_CODEC.encode(buf, p.hostPos());
                        StorageKey.STREAM_CODEC.encode(buf, p.key());
                        buf.writeVarLong(p.quantity());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_SERVERS))
                                .encode(buf, p.serverKeys());
                        buf.writeVarInt(p.destKind());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.destServer());
                    },
                    buf -> new TerminalSelectPayload(
                            BlockPos.STREAM_CODEC.decode(buf),
                            BlockPos.STREAM_CODEC.decode(buf),
                            StorageKey.STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_SERVERS)).decode(buf),
                            buf.readVarInt(),
                            ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override
    public CustomPacketPayload.Type<TerminalSelectPayload> type() {
        return TYPE;
    }
}
