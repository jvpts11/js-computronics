/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei.payload;

import dev.jsc.jscomputronics.JsComputronics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client to server: a crafting recipe the player transferred from the recipe viewer, as the nine cells of the
 * Pattern Studio's bench on the computer at {@code host} (seen from the monitor at {@code monitorPos}), and the
 * recipe's id so the server can mark the cells whose ingredient is a tag.
 */
public record SetPatternPayload(BlockPos host, BlockPos monitorPos, List<ItemStack> grid, String recipeId)
        implements CustomPacketPayload {

    public static final Type<SetPatternPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "set_pattern"));

    public static final int MAX_ID = 128;

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPatternPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetPatternPayload::host,
                    BlockPos.STREAM_CODEC, SetPatternPayload::monitorPos,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(16)), SetPatternPayload::grid,
                    ByteBufCodecs.stringUtf8(MAX_ID), SetPatternPayload::recipeId,
                    SetPatternPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
