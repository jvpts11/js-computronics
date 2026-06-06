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
 * Client to server: the player clicked the network view while holding an item on the cursor, to deposit it into the network (INSERT) — the way a storage terminal accepts items.
 */
public record NetworkInsertPayload(BlockPos pcPos, boolean all) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NetworkInsertPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_insert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkInsertPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NetworkInsertPayload::pcPos,
                    ByteBufCodecs.BOOL, NetworkInsertPayload::all,
                    NetworkInsertPayload::new);

    @Override
    public CustomPacketPayload.Type<NetworkInsertPayload> type() {
        return TYPE;
    }
}
