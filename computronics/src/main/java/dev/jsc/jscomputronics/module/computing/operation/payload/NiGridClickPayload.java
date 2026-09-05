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
 * Client to server: the Network Interactor desktop window clicked an item in a storage grid. This is
 * the desktop equivalent of the terminal's network-grid click, but routed to the host by position
 * (the desktop is a plain Screen, not a container menu): a {@code NET_TO_LOCAL} click pulls the item
 * from the network into the host's local storage; a {@code LOCAL_TO_INV} click withdraws it from local
 * storage into the player's inventory.
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 * @param key        the storage key (item) clicked
 * @param amount     how many to move
 * @param mode       {@link #MODE_NET_TO_LOCAL}, {@link #MODE_LOCAL_TO_INV} or {@link #MODE_LOCAL_TO_NET}
 */
public record NiGridClickPayload(BlockPos host, BlockPos monitorPos, StorageKey key, long amount,
                                 int mode) implements CustomPacketPayload {

    public static final int MODE_NET_TO_LOCAL = 0;
    public static final int MODE_LOCAL_TO_INV = 1;
    /** Upload from the host's local storage into the network (Storage popup "TO NETWORK"). */
    public static final int MODE_LOCAL_TO_NET = 2;

    public static final CustomPacketPayload.Type<NiGridClickPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "ni_grid_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NiGridClickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NiGridClickPayload::host,
                    BlockPos.STREAM_CODEC, NiGridClickPayload::monitorPos,
                    StorageKey.STREAM_CODEC, NiGridClickPayload::key,
                    ByteBufCodecs.VAR_LONG, NiGridClickPayload::amount,
                    ByteBufCodecs.VAR_INT, NiGridClickPayload::mode,
                    NiGridClickPayload::new);

    @Override
    public CustomPacketPayload.Type<NiGridClickPayload> type() {
        return TYPE;
    }
}
