/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import dev.jstech.computers.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Client to server: a Storage tab hands a stack to the computer's local storage. The source is a
 * player-inventory menu slot (shift-click, the whole stack as items), the cursor ({@link #CURSOR}, the whole
 * stack as items) or the cursor on a right-click ({@link #CURSOR_ONE}: one item, or what a held container
 * holds); with {@code CURSOR_ONE}, {@code entry} names the fluid or chemical entry under the cursor so a held
 * empty container fills from it instead.
 */
public record TerminalLocalDepositPayload(BlockPos monitorPos, BlockPos hostPos, int slotIndex,
                                          Optional<StorageKey> entry)
        implements CustomPacketPayload {

    public static final int CURSOR = -1;

    public static final int CURSOR_ONE = -2;

    public static final CustomPacketPayload.Type<TerminalLocalDepositPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_local_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalLocalDepositPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalLocalDepositPayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalLocalDepositPayload::hostPos,
                    ByteBufCodecs.VAR_INT, TerminalLocalDepositPayload::slotIndex,
                    ByteBufCodecs.optional(StorageKey.STREAM_CODEC), TerminalLocalDepositPayload::entry,
                    TerminalLocalDepositPayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalLocalDepositPayload> type() {
        return TYPE;
    }
}
