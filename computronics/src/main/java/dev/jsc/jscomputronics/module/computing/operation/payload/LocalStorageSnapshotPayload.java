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
 * Server to client: the contents of the computer's own local storage (the union of its disks), sent when the Storage tab opens and after each local withdraw/deposit/privacy change. Carries the item list (a type → quantity view, not vanilla slots) plus one {@link DiskInfo} per disk so the Storage tab can draw the per-disk public/private slider with its readout.
 */
public record LocalStorageSnapshotPayload(List<NetworkItemEntry> items, List<DiskInfo> disks)
        implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 256;
    public static final int MAX_DISKS = 8;

    /**
     * One disk's privacy state for the slider: its public-share permille (0..1000) and how much data weight it holds versus its capacity weight.
     */
    public record DiskInfo(int permille, long usedWeight, long capacityWeight) {

        public static final StreamCodec<RegistryFriendlyByteBuf, DiskInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DiskInfo::permille,
                ByteBufCodecs.VAR_LONG, DiskInfo::usedWeight,
                ByteBufCodecs.VAR_LONG, DiskInfo::capacityWeight,
                DiskInfo::new);
    }

    public static final CustomPacketPayload.Type<LocalStorageSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "local_storage_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocalStorageSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    LocalStorageSnapshotPayload::items,
                    DiskInfo.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DISKS)),
                    LocalStorageSnapshotPayload::disks,
                    LocalStorageSnapshotPayload::new);

    @Override
    public CustomPacketPayload.Type<LocalStorageSnapshotPayload> type() {
        return TYPE;
    }
}
