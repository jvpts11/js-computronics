/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client to server: a DROP from the terminal's Maintenance tab — destroy stored data, irreversibly, at the chosen scope.
 */
public record TerminalDropPayload(BlockPos monitorPos, BlockPos hostPos, int scope,
                                  List<StorageKey> types, String serverKey) implements CustomPacketPayload {

    public static final int SCOPE_NETWORK = 0;
    public static final int SCOPE_SERVER = 1;
    public static final int SCOPE_TYPES = 2;

    public static final int MAX_TYPES = 128;

    public static final CustomPacketPayload.Type<TerminalDropPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_drop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalDropPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalDropPayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalDropPayload::hostPos,
                    ByteBufCodecs.VAR_INT, TerminalDropPayload::scope,
                    StorageKey.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TYPES)), TerminalDropPayload::types,
                    ByteBufCodecs.stringUtf8(48), TerminalDropPayload::serverKey,
                    TerminalDropPayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalDropPayload> type() {
        return TYPE;
    }
}
