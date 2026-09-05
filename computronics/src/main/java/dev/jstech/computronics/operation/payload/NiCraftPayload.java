/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: the Network Interactor desktop window requested a craft. The same network CRAFT
 * operation the terminal submits, addressed to the host by position so it works without a menu.
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 * @param result     the product to craft
 * @param amount     how many to craft
 */
public record NiCraftPayload(BlockPos host, BlockPos monitorPos, ItemStack result,
                             long amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NiCraftPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "ni_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NiCraftPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NiCraftPayload::host,
                    BlockPos.STREAM_CODEC, NiCraftPayload::monitorPos,
                    ItemStack.STREAM_CODEC, NiCraftPayload::result,
                    ByteBufCodecs.VAR_LONG, NiCraftPayload::amount,
                    NiCraftPayload::new);

    @Override
    public CustomPacketPayload.Type<NiCraftPayload> type() {
        return TYPE;
    }
}
