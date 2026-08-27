/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei.payload;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client → server: the recipe the player transferred from the recipe viewer into the Pattern Encoder's
 * processing tab — its inputs and outputs as data cells (item, fluid or chemical, with an amount and whether
 * that amount is an estimate), in display order.
 */
public record SetProcessingPatternPayload(BlockPos pos, List<PatternEncoderBlockEntity.DataCell> inputs,
                                          List<PatternEncoderBlockEntity.DataCell> outputs)
        implements CustomPacketPayload {

    public static final Type<SetProcessingPatternPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "jei_set_processing_pattern"));

    private static final int MAX_CELLS = PatternEncoderBlockEntity.PROC_GRID;

    public static final StreamCodec<RegistryFriendlyByteBuf, SetProcessingPatternPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetProcessingPatternPayload::pos,
                    PatternEncoderBlockEntity.DataCell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)),
                    SetProcessingPatternPayload::inputs,
                    PatternEncoderBlockEntity.DataCell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)),
                    SetProcessingPatternPayload::outputs,
                    SetProcessingPatternPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
