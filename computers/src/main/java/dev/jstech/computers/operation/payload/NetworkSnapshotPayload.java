/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the current contents of the network, sent when a Personal Computer opens the Network tab and after each operation, so the GUI can list the items available to SELECT.
 */
public record NetworkSnapshotPayload(List<NetworkItemEntry> items) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 256;

    public static final CustomPacketPayload.Type<NetworkSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSnapshotPayload> STREAM_CODEC =
            NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES))
                    .map(NetworkSnapshotPayload::new, NetworkSnapshotPayload::items);

    @Override
    public CustomPacketPayload.Type<NetworkSnapshotPayload> type() {
        return TYPE;
    }
}
