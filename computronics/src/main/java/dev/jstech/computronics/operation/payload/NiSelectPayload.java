/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.operation.OperationPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Client to server: an advanced request from the Network Interactor — pull a quantity of a type from a
 * chosen set of source Servers, into a chosen destination. Mirrors the MC-NET terminal's SELECT so the NI
 * reuses the same dispatch ({@code resolveDest} + {@code submitNetworkSelect}/{@code submitNetworkMove}).
 *
 * <p>The destination is encoded as a single key: {@code ""} means this computer's own local storage (a
 * plain SELECT); a non-empty Server/computer key means a MOVE into that target.
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 * @param key        the data type requested
 * @param quantity   how many to pull
 * @param serverKeys the chosen source Server keys; empty means "all sources"
 * @param destKey    the destination key ({@code ""} = this computer, else a Server/computer key)
 * @param priority   the level the Operation is scheduled at
 */
public record NiSelectPayload(BlockPos host, BlockPos monitorPos, StorageKey key, long quantity,
                              List<String> serverKeys, String destKey,
                              OperationPriority priority) implements CustomPacketPayload {

    public static final int MAX_SERVERS = 64;
    private static final int KEY_LENGTH = 64;

    public static final CustomPacketPayload.Type<NiSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "ni_select"));

    // Built by hand because the record has more components than StreamCodec.composite carries.
    public static final StreamCodec<RegistryFriendlyByteBuf, NiSelectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        BlockPos.STREAM_CODEC.encode(buf, p.host());
                        BlockPos.STREAM_CODEC.encode(buf, p.monitorPos());
                        StorageKey.STREAM_CODEC.encode(buf, p.key());
                        buf.writeVarLong(p.quantity());
                        final int count = Math.min(p.serverKeys().size(), MAX_SERVERS);
                        buf.writeVarInt(count);
                        for (int i = 0; i < count; i++) {
                            ByteBufCodecs.stringUtf8(KEY_LENGTH).encode(buf, p.serverKeys().get(i));
                        }
                        ByteBufCodecs.stringUtf8(KEY_LENGTH).encode(buf, p.destKey());
                        NiGridClickPayload.PRIORITY_CODEC.encode(buf, p.priority());
                    },
                    buf -> {
                        final BlockPos host = BlockPos.STREAM_CODEC.decode(buf);
                        final BlockPos monitor = BlockPos.STREAM_CODEC.decode(buf);
                        final StorageKey key = StorageKey.STREAM_CODEC.decode(buf);
                        final long quantity = buf.readVarLong();
                        final int count = Math.min(buf.readVarInt(), MAX_SERVERS);
                        final List<String> sources = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            sources.add(ByteBufCodecs.stringUtf8(KEY_LENGTH).decode(buf));
                        }
                        final String destKey = ByteBufCodecs.stringUtf8(KEY_LENGTH).decode(buf);
                        final OperationPriority priority = NiGridClickPayload.PRIORITY_CODEC.decode(buf);
                        return new NiSelectPayload(host, monitor, key, quantity, List.copyOf(sources), destKey,
                                priority);
                    });

    @Override
    public CustomPacketPayload.Type<NiSelectPayload> type() {
        return TYPE;
    }
}
