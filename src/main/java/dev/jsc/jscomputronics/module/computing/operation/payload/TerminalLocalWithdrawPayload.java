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
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: the player clicked an item in the Storage tab to WITHDRAW up to {@code quantity} of {@code stack}'s exact type (item + components) from the computer's local storage into their inventory.
 */
public record TerminalLocalWithdrawPayload(BlockPos monitorPos, BlockPos hostPos, ItemStack stack, long quantity)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TerminalLocalWithdrawPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_local_withdraw"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalLocalWithdrawPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalLocalWithdrawPayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalLocalWithdrawPayload::hostPos,
                    ItemStack.OPTIONAL_STREAM_CODEC, TerminalLocalWithdrawPayload::stack,
                    ByteBufCodecs.VAR_LONG, TerminalLocalWithdrawPayload::quantity,
                    TerminalLocalWithdrawPayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalLocalWithdrawPayload> type() {
        return TYPE;
    }
}
