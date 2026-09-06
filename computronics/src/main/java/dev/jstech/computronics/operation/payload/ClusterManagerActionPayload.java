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
 * Client to server: something the player did in the Cluster Manager. The cluster is addressed by kind
 * and index (the order the state listed them in); a per-node action also names the rack and row.
 * Every action is answered with a fresh state for the same selection.
 *
 * @param hostPos the Cluster Management Computer
 * @param action  one of the {@code ACTION_*} constants
 * @param kind    the selected cluster kind (0 supercomputer, 1 datacenter, 2 AI)
 * @param index   the selected cluster's index within its kind
 * @param rackPos the rack, for a per-node action (as a long; ignored otherwise)
 * @param row     the unit row, for a per-node action (ignored otherwise)
 */
public record ClusterManagerActionPayload(BlockPos hostPos, int action, int kind, int index, long rackPos, int row)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClusterManagerActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "cluster_manager_action"));

    public static final int ACTION_REFRESH = 0;
    public static final int ACTION_INSTALL_SYSTEM_ALL = 1;
    public static final int ACTION_INSTALL_PROGRAM_ALL = 2;
    public static final int ACTION_POWER_ALL_ON = 3;
    public static final int ACTION_POWER_ALL_OFF = 4;
    public static final int ACTION_TOGGLE_NODE = 5;
    public static final int ACTION_CANCEL_JOB = 6;
    public static final int ACTION_CYCLE_BALANCE = 7;
    public static final int ACTION_DEPOSIT = 8;
    public static final int ACTION_DEPOSIT_ONE = 9;
    public static final int ACTION_INSTALL_SYSTEM_NODE = 10;
    public static final int ACTION_INSTALL_PROGRAM_NODE = 11;

    public static final StreamCodec<RegistryFriendlyByteBuf, ClusterManagerActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ClusterManagerActionPayload::hostPos,
                    ByteBufCodecs.VAR_INT, ClusterManagerActionPayload::action,
                    ByteBufCodecs.VAR_INT, ClusterManagerActionPayload::kind,
                    ByteBufCodecs.VAR_INT, ClusterManagerActionPayload::index,
                    ByteBufCodecs.VAR_LONG, ClusterManagerActionPayload::rackPos,
                    ByteBufCodecs.VAR_INT, ClusterManagerActionPayload::row,
                    ClusterManagerActionPayload::new);

    public static ClusterManagerActionPayload bulk(final BlockPos hostPos, final int action, final int kind, final int index) {
        return new ClusterManagerActionPayload(hostPos, action, kind, index, 0L, -1);
    }

    @Override
    public CustomPacketPayload.Type<ClusterManagerActionPayload> type() {
        return TYPE;
    }
}
