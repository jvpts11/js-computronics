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
 * Client to server: submit the CRAFT the popup configured.
 */
public record CraftSubmitPayload(BlockPos monitorPos, BlockPos hostPos,
                                 ItemStack result, long quantity, boolean partial)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CraftSubmitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "craft_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftSubmitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CraftSubmitPayload::monitorPos,
                    BlockPos.STREAM_CODEC, CraftSubmitPayload::hostPos,
                    ItemStack.STREAM_CODEC, CraftSubmitPayload::result,
                    ByteBufCodecs.VAR_LONG, CraftSubmitPayload::quantity,
                    ByteBufCodecs.BOOL, CraftSubmitPayload::partial,
                    CraftSubmitPayload::new);

    @Override
    public CustomPacketPayload.Type<CraftSubmitPayload> type() {
        return TYPE;
    }
}
