/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Client to server: the Network Interactor hands the player's held cursor stack to the network (Network tab)
 * or the host's local storage (Storage tab), mirroring the MC-NET terminal's deposit. A {@code whole} deposit
 * pushes the entire held stack as items; otherwise ONE is handed over — one item, or what a held container
 * holds — and, when the click landed on a fluid or chemical entry a held empty container could take,
 * {@code entry} names it so the container fills from it instead.
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 * @param target     {@link #TARGET_NETWORK} or {@link #TARGET_STORAGE}
 * @param whole      true to deposit the whole held stack, false to hand over one
 * @param entry      the grid entry under the cursor on a right-click, if any
 */
public record NiDepositPayload(BlockPos host, BlockPos monitorPos, int target, boolean whole,
                               Optional<StorageKey> entry)
        implements CustomPacketPayload {

    public static final int TARGET_NETWORK = 0;
    public static final int TARGET_STORAGE = 1;

    public static final CustomPacketPayload.Type<NiDepositPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "ni_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NiDepositPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NiDepositPayload::host,
                    BlockPos.STREAM_CODEC, NiDepositPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, NiDepositPayload::target,
                    ByteBufCodecs.BOOL, NiDepositPayload::whole,
                    ByteBufCodecs.optional(StorageKey.STREAM_CODEC), NiDepositPayload::entry,
                    NiDepositPayload::new);

    @Override
    public CustomPacketPayload.Type<NiDepositPayload> type() {
        return TYPE;
    }
}
