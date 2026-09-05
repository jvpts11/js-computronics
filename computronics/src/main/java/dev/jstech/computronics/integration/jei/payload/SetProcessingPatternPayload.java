/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.integration.jei.payload;

import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.crafting.PatternWorkbench;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client to server: a recipe the player transferred from the recipe viewer into the Pattern Studio's machine
 * draft on the computer at {@code host}: its inputs and outputs as data cells (item, fluid or chemical, with an
 * amount and whether that amount is an estimate), in display order.
 */
public record SetProcessingPatternPayload(BlockPos host, BlockPos monitorPos,
                                          List<PatternWorkbench.DataCell> inputs,
                                          List<PatternWorkbench.DataCell> outputs,
                                          String recipeType)
        implements CustomPacketPayload {

    public static final Type<SetProcessingPatternPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "jei_set_processing_pattern"));

    private static final int MAX_CELLS = PatternWorkbench.PROC_GRID;
    /** The recipe type id ("minecraft:smelting"), which the server maps to a machine; empty when unknown. */
    public static final int MAX_TYPE = 96;

    public static final StreamCodec<RegistryFriendlyByteBuf, SetProcessingPatternPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetProcessingPatternPayload::host,
                    BlockPos.STREAM_CODEC, SetProcessingPatternPayload::monitorPos,
                    PatternWorkbench.DataCell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)),
                    SetProcessingPatternPayload::inputs,
                    PatternWorkbench.DataCell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)),
                    SetProcessingPatternPayload::outputs,
                    ByteBufCodecs.stringUtf8(MAX_TYPE), SetProcessingPatternPayload::recipeType,
                    SetProcessingPatternPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
