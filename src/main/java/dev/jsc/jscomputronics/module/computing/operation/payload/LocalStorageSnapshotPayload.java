/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the contents of the computer's own local storage (the union of its disks), sent when the Storage tab opens and after each local withdraw/deposit, so the Storage tab can list the items held — as a type → quantity view, not vanilla slots.
 */
public record LocalStorageSnapshotPayload(List<NetworkItemEntry> items) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 256;

    public static final CustomPacketPayload.Type<LocalStorageSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "local_storage_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocalStorageSnapshotPayload> STREAM_CODEC =
            NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES))
                    .map(LocalStorageSnapshotPayload::new, LocalStorageSnapshotPayload::items);

    @Override
    public CustomPacketPayload.Type<LocalStorageSnapshotPayload> type() {
        return TYPE;
    }
}
