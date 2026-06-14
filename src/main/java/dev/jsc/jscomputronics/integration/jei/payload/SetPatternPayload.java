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
 * Client-to-server payload that writes a 9-cell ingredient list into the
 * ghost grid of the Pattern Encoder the player currently has open.
 *
 * Sent by the JEI recipe-transfer handler when the player clicks "+" on a
 * crafting recipe.  The server validates that the player's open menu
 * corresponds to the indicated block position before applying any changes.
 */
public record SetPatternPayload(BlockPos pos, List<ItemStack> grid)
        implements CustomPacketPayload {

    public static final Type<SetPatternPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "set_pattern"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPatternPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetPatternPayload::pos,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), SetPatternPayload::grid,
                    SetPatternPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
