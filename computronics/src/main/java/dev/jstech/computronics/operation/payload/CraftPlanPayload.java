/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server to client: the recursive planner's answer for the Craft popup — the raw ingredients the request consumes (need vs have, red when short), whether it is fully feasible, the largest feasible amount for the PARTIAL button, and a time estimate.
 */
public record CraftPlanPayload(ItemStack result, long quantity, List<Row> rows,
                               boolean feasible, long maxFeasible, int estimateTicks)
        implements CustomPacketPayload {

    public static final int MAX_ROWS = 64;

    public record Row(ItemStack item, long need, long have) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ItemStack.STREAM_CODEC, Row::item,
                        ByteBufCodecs.VAR_LONG, Row::need,
                        ByteBufCodecs.VAR_LONG, Row::have,
                        Row::new);

        public boolean satisfied() {
            return have >= need;
        }
    }

    public static final CustomPacketPayload.Type<CraftPlanPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "craft_plan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlanPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, CraftPlanPayload::result,
                    ByteBufCodecs.VAR_LONG, CraftPlanPayload::quantity,
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROWS)), CraftPlanPayload::rows,
                    ByteBufCodecs.BOOL, CraftPlanPayload::feasible,
                    ByteBufCodecs.VAR_LONG, CraftPlanPayload::maxFeasible,
                    ByteBufCodecs.VAR_INT, CraftPlanPayload::estimateTicks,
                    CraftPlanPayload::new);

    @Override
    public CustomPacketPayload.Type<CraftPlanPayload> type() {
        return TYPE;
    }
}
