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
 * Client to server: a MOVE out of a Datacenter Station's bound section — pull {@code quantity} of {@code key} from the section's Servers into the computer at {@code destPos} (its local storage).
 */
public record DatacenterSelectPayload(BlockPos stationPos, StorageKey key, long quantity, long destPos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DatacenterSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "datacenter_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DatacenterSelectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DatacenterSelectPayload::stationPos,
                    StorageKey.STREAM_CODEC, DatacenterSelectPayload::key,
                    ByteBufCodecs.VAR_LONG, DatacenterSelectPayload::quantity,
                    ByteBufCodecs.VAR_LONG, DatacenterSelectPayload::destPos,
                    DatacenterSelectPayload::new);

    @Override
    public CustomPacketPayload.Type<DatacenterSelectPayload> type() {
        return TYPE;
    }
}
