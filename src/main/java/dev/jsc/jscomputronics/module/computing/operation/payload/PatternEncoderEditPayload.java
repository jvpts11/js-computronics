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

/**
 * Client to server: one authoring edit to the Pattern Encoder's PROCESSING / MULTI-STAGE state. A single payload
 * multiplexes every edit through an {@code action} code so the encoder needs only one registration. The server is
 * authoritative: it validates the open menu and position, applies the edit to the block entity, and pushes a fresh
 * update tag so the screen reads the result back.
 *
 * <p>{@code index}/{@code value}/{@code text} carry the action's parameters (an unused field is ignored). For the
 * input/output placement actions the server reads the player's carried stack, mirroring the bench ghost grid.
 *
 * @param pos    the Pattern Encoder block entity position
 * @param action one of the {@code ACTION_*} codes
 * @param index  a grid cell or stage index, when the action needs one
 * @param value  an integer parameter (tab, chance percent, timeout ticks), when the action needs one
 * @param text   a string parameter (the machine type), when the action needs one
 */
public record PatternEncoderEditPayload(BlockPos pos, int action, int index, int value, String text)
        implements CustomPacketPayload {

    public static final int ACTION_SET_TAB = 0;
    public static final int ACTION_SET_INPUT = 1;
    public static final int ACTION_SET_OUTPUT = 2;
    public static final int ACTION_SET_CHANCE = 3;
    public static final int ACTION_SET_MACHINE = 4;
    public static final int ACTION_SET_TIMEOUT = 5;
    public static final int ACTION_WRITE_PROC = 6;
    public static final int ACTION_WRITE_MULTI = 7;
    public static final int ACTION_ADD_STAGE_BENCH = 8;
    public static final int ACTION_ADD_STAGE_PROC = 9;
    public static final int ACTION_REMOVE_STAGE = 10;
    public static final int ACTION_CLEAR_STAGES = 11;
    public static final int ACTION_CLEAR_PROC = 12;
    public static final int ACTION_ADD_STAGE_FROM_MEDIA = 13;
    /** Sets a processing cell's amount ({@code value}); {@code text} is "out" for an output cell, else an input. */
    public static final int ACTION_SET_AMOUNT = 14;
    /** Clears a processing cell; {@code text} is "out" for an output cell, else an input. */
    public static final int ACTION_CLEAR_CELL = 15;

    /** Bound on the machine-type string so a forged payload cannot allocate without limit. */
    private static final int MAX_TEXT = 256;

    public PatternEncoderEditPayload {
        text = text == null ? "" : text;
    }

    public static PatternEncoderEditPayload action(final BlockPos pos, final int action) {
        return new PatternEncoderEditPayload(pos, action, 0, 0, "");
    }

    public static PatternEncoderEditPayload indexed(final BlockPos pos, final int action, final int index) {
        return new PatternEncoderEditPayload(pos, action, index, 0, "");
    }

    public static PatternEncoderEditPayload indexedValued(final BlockPos pos, final int action,
                                                          final int index, final int value) {
        return new PatternEncoderEditPayload(pos, action, index, value, "");
    }

    public static PatternEncoderEditPayload valued(final BlockPos pos, final int action, final int value) {
        return new PatternEncoderEditPayload(pos, action, 0, value, "");
    }

    public static PatternEncoderEditPayload texted(final BlockPos pos, final int action, final String text) {
        return new PatternEncoderEditPayload(pos, action, 0, 0, text);
    }

    public static final CustomPacketPayload.Type<PatternEncoderEditPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "pattern_encoder_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternEncoderEditPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PatternEncoderEditPayload::pos,
                    ByteBufCodecs.VAR_INT, PatternEncoderEditPayload::action,
                    ByteBufCodecs.VAR_INT, PatternEncoderEditPayload::index,
                    ByteBufCodecs.VAR_INT, PatternEncoderEditPayload::value,
                    ByteBufCodecs.stringUtf8(MAX_TEXT), PatternEncoderEditPayload::text,
                    PatternEncoderEditPayload::new);

    @Override
    public CustomPacketPayload.Type<PatternEncoderEditPayload> type() {
        return TYPE;
    }
}
