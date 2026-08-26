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
 * Client-to-server payload that writes a machine recipe's inputs and outputs into the processing draft of the
 * Pattern Encoder the player currently has open.
 *
 * Sent by the universal JEI recipe-transfer handler when the player clicks "+" on any non-crafting recipe
 * (smelting, mod machine categories, ...). The server validates that the player's open menu corresponds to the
 * indicated block position before applying any changes.
 */
public record SetProcessingPatternPayload(BlockPos pos, List<ItemStack> inputs, List<ItemStack> outputs)
        implements CustomPacketPayload {

    public static final Type<SetProcessingPatternPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "set_processing_pattern"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetProcessingPatternPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetProcessingPatternPayload::pos,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(32)),
                    SetProcessingPatternPayload::inputs,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(32)),
                    SetProcessingPatternPayload::outputs,
                    SetProcessingPatternPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
