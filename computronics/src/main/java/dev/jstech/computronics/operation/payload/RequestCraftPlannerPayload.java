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
 * Client to server: the Craft Planner asks for data. With an empty {@code target} it requests the craft
 * catalogue (the pickable items, replied with a {@link CraftCatalogPayload}); with a real target it requests
 * a plan for {@code quantity} of it (replied with a {@link CraftPlannerPayload}). Proximity + monitor-link
 * gated like the Network Interactor.
 */
public record RequestCraftPlannerPayload(BlockPos host, BlockPos monitorPos, ItemStack target, long quantity)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestCraftPlannerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_craft_planner"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCraftPlannerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestCraftPlannerPayload::host,
                    BlockPos.STREAM_CODEC, RequestCraftPlannerPayload::monitorPos,
                    ItemStack.OPTIONAL_STREAM_CODEC, RequestCraftPlannerPayload::target,
                    ByteBufCodecs.VAR_LONG, RequestCraftPlannerPayload::quantity,
                    RequestCraftPlannerPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestCraftPlannerPayload> type() {
        return TYPE;
    }
}
