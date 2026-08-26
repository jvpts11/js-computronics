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
 * Client to server: the Network Interactor desktop window clicked one of the player's inventory slots,
 * shown in the window as the deposit source. Since a desktop Screen has no menu cursor, the inventory
 * slot is the item source: an {@code INV_TO_NET} click pushes that slot's stack into the network; an
 * {@code INV_TO_LOCAL} click deposits it into the host's local storage.
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 * @param slot       the player inventory slot index (0-35: 0-8 hotbar, 9-35 main)
 * @param mode       {@link #MODE_INV_TO_NET} or {@link #MODE_INV_TO_LOCAL}
 */
public record NiHotbarClickPayload(BlockPos host, BlockPos monitorPos, int slot,
                                   int mode) implements CustomPacketPayload {

    public static final int MODE_INV_TO_NET = 0;
    public static final int MODE_INV_TO_LOCAL = 1;

    public static final CustomPacketPayload.Type<NiHotbarClickPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "ni_hotbar_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NiHotbarClickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NiHotbarClickPayload::host,
                    BlockPos.STREAM_CODEC, NiHotbarClickPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, NiHotbarClickPayload::slot,
                    ByteBufCodecs.VAR_INT, NiHotbarClickPayload::mode,
                    NiHotbarClickPayload::new);

    @Override
    public CustomPacketPayload.Type<NiHotbarClickPayload> type() {
        return TYPE;
    }
}
