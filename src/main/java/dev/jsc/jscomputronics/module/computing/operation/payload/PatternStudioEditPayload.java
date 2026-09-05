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
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: one edit to the Pattern Studio's workbench on the computer at {@code host}, made from the
 * monitor at {@code monitorPos}. The server is authoritative: it applies the edit to the machine's workbench
 * and answers with a fresh {@link PatternStudioStatePayload}. Which of {@code index}, {@code value},
 * {@code text}, {@code text2} and {@code item} an action reads is listed on each action constant.
 */
public record PatternStudioEditPayload(BlockPos host, BlockPos monitorPos, int action, int index, long value,
                                       String text, String text2, ItemStack item) implements CustomPacketPayload {

    public static final int MAX_TEXT = 128;
    public static final int MAX_TEXT2 = 256;

    // Bench draft.
    /** Sets bench cell {@code index} to a ghost of {@code item} (an empty item clears the cell). */
    public static final int BENCH_SET_CELL = 1;
    /** Clears bench cell {@code index}. */
    public static final int BENCH_CLEAR_CELL = 2;
    /** Makes bench cell {@code index} accept any item of tag {@code text} ({@code ""} makes it exact). */
    public static final int BENCH_SET_TAG = 3;
    /** Names the bench draft {@code text} with note {@code text2}. */
    public static final int BENCH_SET_NAME = 4;
    /** Clears the whole bench draft. */
    public static final int BENCH_CLEAR = 5;

    // Machine draft.
    /** Sets input cell {@code index} from {@code item} (a container places its fluid or chemical). */
    public static final int PROC_SET_INPUT = 10;
    /** Sets output cell {@code index} from {@code item}. */
    public static final int PROC_SET_OUTPUT = 11;
    public static final int PROC_CLEAR_INPUT = 12;
    public static final int PROC_CLEAR_OUTPUT = 13;
    /** Sets input cell {@code index}'s amount per run to {@code value} (0 clears it). */
    public static final int PROC_SET_INPUT_AMOUNT = 14;
    public static final int PROC_SET_OUTPUT_AMOUNT = 15;
    /** Sets output cell {@code index}'s chance to {@code value} percent. */
    public static final int PROC_SET_CHANCE = 16;
    /** Sets the machine type to {@code text}. */
    public static final int PROC_SET_MACHINE = 17;
    /** Sets the timeout to {@code value} ticks. */
    public static final int PROC_SET_TIMEOUT = 18;
    public static final int PROC_SET_NAME = 19;
    public static final int PROC_CLEAR = 20;

    // Pipeline draft.
    public static final int PIPE_ADD_BENCH = 30;
    public static final int PIPE_ADD_PROC = 31;
    /** Appends the pattern in file {@code text2} on drive {@code text} as a stage. */
    public static final int PIPE_ADD_FILE = 32;
    public static final int PIPE_REMOVE = 33;
    public static final int PIPE_SET_NAME = 34;
    public static final int PIPE_CLEAR = 35;

    // Files and outputs. {@code index} is the draft kind (0 bench, 1 machine, 2 pipeline) where one applies.
    /** Opens file {@code text2} on drive {@code text} into the draft of its kind. */
    public static final int OPEN_FILE = 40;
    /** Writes draft {@code index} to the system disk's crafts folder. */
    public static final int SAVE_TO_DISK = 41;
    /** Loads draft {@code index} into this Crafting Computer's Recipe ROM. */
    public static final int LOAD_INTO_ROM = 42;
    /** Sends draft {@code index} to the linked Pattern Encoder. */
    public static final int BURN = 43;
    public static final int ENCODER_CANCEL = 44;
    public static final int ENCODER_EJECT = 45;

    public static final CustomPacketPayload.Type<PatternStudioEditPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "pattern_studio_edit"));

    // Two nested records keep each composite inside the six-pair limit.
    private record Target(BlockPos host, BlockPos monitorPos) {
        static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Target::host,
                BlockPos.STREAM_CODEC, Target::monitorPos,
                Target::new);
    }

    private record Body(int action, int index, long value, String text, String text2, ItemStack item) {
        static final StreamCodec<RegistryFriendlyByteBuf, Body> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Body::action,
                ByteBufCodecs.VAR_INT, Body::index,
                ByteBufCodecs.VAR_LONG, Body::value,
                ByteBufCodecs.stringUtf8(MAX_TEXT), Body::text,
                ByteBufCodecs.stringUtf8(MAX_TEXT2), Body::text2,
                ItemStack.OPTIONAL_STREAM_CODEC, Body::item,
                Body::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternStudioEditPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Target.STREAM_CODEC, p -> new Target(p.host(), p.monitorPos()),
                    Body.STREAM_CODEC, p -> new Body(p.action(), p.index(), p.value(), p.text(), p.text2(), p.item()),
                    (t, b) -> new PatternStudioEditPayload(t.host(), t.monitorPos(), b.action(), b.index(), b.value(),
                            b.text(), b.text2(), b.item()));

    /** An edit with no payload beyond its action. */
    public static PatternStudioEditPayload of(final BlockPos host, final BlockPos monitorPos, final int action) {
        return new PatternStudioEditPayload(host, monitorPos, action, 0, 0L, "", "", ItemStack.EMPTY);
    }

    public static PatternStudioEditPayload at(final BlockPos host, final BlockPos monitorPos, final int action,
                                              final int index) {
        return new PatternStudioEditPayload(host, monitorPos, action, index, 0L, "", "", ItemStack.EMPTY);
    }

    public static PatternStudioEditPayload item(final BlockPos host, final BlockPos monitorPos, final int action,
                                                final int index, final ItemStack item) {
        return new PatternStudioEditPayload(host, monitorPos, action, index, 0L, "", "", item.copy());
    }

    public static PatternStudioEditPayload number(final BlockPos host, final BlockPos monitorPos, final int action,
                                                  final int index, final long value) {
        return new PatternStudioEditPayload(host, monitorPos, action, index, value, "", "", ItemStack.EMPTY);
    }

    public static PatternStudioEditPayload text(final BlockPos host, final BlockPos monitorPos, final int action,
                                                final int index, final String text, final String text2) {
        return new PatternStudioEditPayload(host, monitorPos, action, index, 0L, clip(text, MAX_TEXT),
                clip(text2, MAX_TEXT2), ItemStack.EMPTY);
    }

    private static String clip(final String s, final int max) {
        final String v = s == null ? "" : s;
        return v.length() <= max ? v : v.substring(0, max);
    }

    @Override
    public CustomPacketPayload.Type<PatternStudioEditPayload> type() {
        return TYPE;
    }
}
