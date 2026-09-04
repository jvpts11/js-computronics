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

/**
 * Client to server: move items out of a datacenter section into a computer's local storage, from the
 * Cluster Manager's inventory view. Routed as a MOVE Operation through the Mainframe with the section's
 * servers as the only sources.
 *
 * @param hostPos  the Cluster Management Computer
 * @param index    the datacenter section's index in the state's list
 * @param key      what to move
 * @param quantity how much
 * @param destPos  the destination computer (a position from the state's destination list)
 */
public record ClusterMoveOutPayload(BlockPos hostPos, int index, StorageKey key, long quantity, long destPos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClusterMoveOutPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "cluster_move_out"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClusterMoveOutPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ClusterMoveOutPayload::hostPos,
                    ByteBufCodecs.VAR_INT, ClusterMoveOutPayload::index,
                    StorageKey.STREAM_CODEC, ClusterMoveOutPayload::key,
                    ByteBufCodecs.VAR_LONG, ClusterMoveOutPayload::quantity,
                    ByteBufCodecs.VAR_LONG, ClusterMoveOutPayload::destPos,
                    ClusterMoveOutPayload::new);

    @Override
    public CustomPacketPayload.Type<ClusterMoveOutPayload> type() {
        return TYPE;
    }
}
