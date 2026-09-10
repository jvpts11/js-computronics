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
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: the open Craft popup asks for the bill of materials of crafting {@code quantity} of
 * {@code result}. {@code recipe} picks which of the recipes that make the result to plan with, by its index in
 * the list the reply carries; {@link #ANY} lets the machine choose (the one the player picked last for this
 * item, else the first).
 */
public record CraftPlanRequestPayload(BlockPos monitorPos, BlockPos hostPos,
                                      ItemStack result, long quantity, int recipe) implements CustomPacketPayload {

    /** No recipe named: the machine's own choice. */
    public static final int ANY = -1;

    public CraftPlanRequestPayload(final BlockPos monitorPos, final BlockPos hostPos, final ItemStack result,
                                   final long quantity) {
        this(monitorPos, hostPos, result, quantity, ANY);
    }

    public static final CustomPacketPayload.Type<CraftPlanRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "craft_plan_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlanRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CraftPlanRequestPayload::monitorPos,
                    BlockPos.STREAM_CODEC, CraftPlanRequestPayload::hostPos,
                    ItemStack.STREAM_CODEC, CraftPlanRequestPayload::result,
                    ByteBufCodecs.VAR_LONG, CraftPlanRequestPayload::quantity,
                    ByteBufCodecs.VAR_INT, CraftPlanRequestPayload::recipe,
                    CraftPlanRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<CraftPlanRequestPayload> type() {
        return TYPE;
    }
}
