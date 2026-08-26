/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
 */
public record NiSelectPayload(BlockPos host, BlockPos monitorPos, StorageKey key, long quantity,
                              List<String> serverKeys, String destKey) implements CustomPacketPayload {

    public static final int MAX_SERVERS = 64;

    public static final CustomPacketPayload.Type<NiSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "ni_select"));

    private record FreeKey(String value) {
        static final StreamCodec<RegistryFriendlyByteBuf, FreeKey> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.stringUtf8(64), FreeKey::value, FreeKey::new);
    }

    private record Wire(BlockPos host, BlockPos monitor, StorageKey key, long qty,
                        List<FreeKey> sources, String destKey) {
        static final StreamCodec<RegistryFriendlyByteBuf, Wire> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, Wire::host,
                        BlockPos.STREAM_CODEC, Wire::monitor,
                        StorageKey.STREAM_CODEC, Wire::key,
                        ByteBufCodecs.VAR_LONG, Wire::qty,
                        FreeKey.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SERVERS)), Wire::sources,
                        ByteBufCodecs.stringUtf8(64), Wire::destKey,
                        Wire::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NiSelectPayload> STREAM_CODEC =
            Wire.STREAM_CODEC.map(
                    w -> new NiSelectPayload(w.host(), w.monitor(), w.key(), w.qty(),
                            w.sources().stream().map(FreeKey::value).toList(), w.destKey()),
                    p -> new Wire(p.host(), p.monitorPos(), p.key(), p.quantity(),
                            p.serverKeys().stream().map(FreeKey::new).toList(), p.destKey()));

    @Override
    public CustomPacketPayload.Type<NiSelectPayload> type() {
        return TYPE;
    }
}
