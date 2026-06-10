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
 * Client to server: an action on an open Datacenter Station GUI.
 */
public record DatacenterStationActionPayload(BlockPos stationPos, int action) implements CustomPacketPayload {

    public static final int ACTION_REFRESH = 0;
    public static final int ACTION_NEXT_SECTION = 1;
    public static final int ACTION_CYCLE_BALANCE = 2;
    public static final int ACTION_INSERT_CURSOR = 3;
    public static final int ACTION_INSERT_CURSOR_ONE = 4;

    public static final CustomPacketPayload.Type<DatacenterStationActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "datacenter_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DatacenterStationActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DatacenterStationActionPayload::stationPos,
                    ByteBufCodecs.VAR_INT, DatacenterStationActionPayload::action,
                    DatacenterStationActionPayload::new);

    @Override
    public CustomPacketPayload.Type<DatacenterStationActionPayload> type() {
        return TYPE;
    }
}
