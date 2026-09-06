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
 * Client to server: the open Craft popup asks for the bill of materials of crafting {@code quantity} of {@code result}.
 */
public record CraftPlanRequestPayload(BlockPos monitorPos, BlockPos hostPos,
                                      ItemStack result, long quantity) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CraftPlanRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "craft_plan_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlanRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CraftPlanRequestPayload::monitorPos,
                    BlockPos.STREAM_CODEC, CraftPlanRequestPayload::hostPos,
                    ItemStack.STREAM_CODEC, CraftPlanRequestPayload::result,
                    ByteBufCodecs.VAR_LONG, CraftPlanRequestPayload::quantity,
                    CraftPlanRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<CraftPlanRequestPayload> type() {
        return TYPE;
    }
}
