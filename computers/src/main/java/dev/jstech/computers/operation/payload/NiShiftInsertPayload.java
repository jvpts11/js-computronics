/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a shift-click on an inventory slot in the Network Interactor inserts that whole stack into
 * the network (Network tab) or into this computer's local storage (Local tab), like MC-NET. The slot is the
 * player-inventory index; the server reads the stack there, so the held cursor is irrelevant (unlike
 * {@link NiDepositPayload}, which deposits the cursor).
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 * @param slot       the player-inventory slot index to pull the stack from
 * @param target     {@link #TARGET_NETWORK} or {@link #TARGET_STORAGE}
 */
public record NiShiftInsertPayload(BlockPos host, BlockPos monitorPos, int slot, int target)
        implements CustomPacketPayload {

    public static final int TARGET_NETWORK = 0;
    public static final int TARGET_STORAGE = 1;

    public static final CustomPacketPayload.Type<NiShiftInsertPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "ni_shift_insert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NiShiftInsertPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NiShiftInsertPayload::host,
                    BlockPos.STREAM_CODEC, NiShiftInsertPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, NiShiftInsertPayload::slot,
                    ByteBufCodecs.VAR_INT, NiShiftInsertPayload::target,
                    NiShiftInsertPayload::new);

    @Override
    public CustomPacketPayload.Type<NiShiftInsertPayload> type() {
        return TYPE;
    }
}
