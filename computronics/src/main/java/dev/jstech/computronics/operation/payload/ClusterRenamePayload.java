/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player named a cluster in the Cluster Manager. A datacenter section keeps the
 * name on its router, per face; a supercomputer keeps it on its HBW Interface. An empty name goes back
 * to the default ("Router · EAST", "SC-1"). Answered with a fresh state for the same selection.
 *
 * @param hostPos the Cluster Management Computer
 * @param kind    the selected cluster kind (0 supercomputer, 1 datacenter)
 * @param index   the selected cluster's index within its kind
 * @param name    the new name, at most {@link #MAX_NAME} characters
 */
public record ClusterRenamePayload(BlockPos hostPos, int kind, int index, String name) implements CustomPacketPayload {

    public static final int MAX_NAME = 24;

    public static final CustomPacketPayload.Type<ClusterRenamePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "cluster_rename"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClusterRenamePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ClusterRenamePayload::hostPos,
                    ByteBufCodecs.VAR_INT, ClusterRenamePayload::kind,
                    ByteBufCodecs.VAR_INT, ClusterRenamePayload::index,
                    ByteBufCodecs.stringUtf8(64), ClusterRenamePayload::name,
                    ClusterRenamePayload::new);

    @Override
    public CustomPacketPayload.Type<ClusterRenamePayload> type() {
        return TYPE;
    }
}
