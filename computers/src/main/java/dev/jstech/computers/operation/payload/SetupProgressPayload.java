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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What a machine says about the program it is setting up, to every desktop looking at it.
 *
 * <p>Sent while the job runs, and once more when it ends, with how it ended: done, refused with a
 * reason, or cancelled. A desktop opens its Setup window on the first of these and closes it after the
 * last, so the window is a view of the machine's job and never the other way round.
 *
 * @param hostPos   the machine
 * @param programId the program's registry path, which is also its icon
 * @param name      what to call it
 * @param house     who publishes it
 * @param sizeMb    what it takes on the disk
 * @param source    where it comes from, worded: "DVD", "the Mirror"
 * @param permille  how far along, in thousandths
 * @param phase     what is happening right now
 * @param state     one of the {@code STATE_} values
 * @param message   why it was refused, or empty
 * @param removing  taking the program off rather than putting it on
 */
public record SetupProgressPayload(BlockPos hostPos, String programId, String name, String house,
                                   int sizeMb, String source, int permille, String phase, int state,
                                   String message, boolean removing) implements CustomPacketPayload {

    public static final int STATE_RUNNING = 0;
    public static final int STATE_DONE = 1;
    public static final int STATE_REFUSED = 2;
    public static final int STATE_CANCELLED = 3;

    private static final int MAX_ID = 64;
    private static final int MAX_NAME = 64;
    private static final int MAX_TEXT = 160;

    public static final CustomPacketPayload.Type<SetupProgressPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "setup_progress"));

    /*
     * Eleven fields is past what composite() takes, so the codec is written out. Every string is capped
     * on both sides, because a cap on a payload string does not cut what is too long, it fails to send.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SetupProgressPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeBlockPos(p.hostPos());
                buf.writeUtf(clip(p.programId(), MAX_ID), MAX_ID);
                buf.writeUtf(clip(p.name(), MAX_NAME), MAX_NAME);
                buf.writeUtf(clip(p.house(), MAX_NAME), MAX_NAME);
                buf.writeVarInt(p.sizeMb());
                buf.writeUtf(clip(p.source(), MAX_NAME), MAX_NAME);
                buf.writeVarInt(p.permille());
                buf.writeUtf(clip(p.phase(), MAX_NAME), MAX_NAME);
                buf.writeVarInt(p.state());
                buf.writeUtf(clip(p.message(), MAX_TEXT), MAX_TEXT);
                buf.writeBoolean(p.removing());
            }, buf -> new SetupProgressPayload(
                    buf.readBlockPos(), buf.readUtf(MAX_ID), buf.readUtf(MAX_NAME), buf.readUtf(MAX_NAME),
                    buf.readVarInt(), buf.readUtf(MAX_NAME), buf.readVarInt(), buf.readUtf(MAX_NAME),
                    buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readBoolean()));

    /** A string never longer than the wire allows, so a long refusal is shortened rather than fatal. */
    private static String clip(final String text, final int max) {
        final String s = text == null ? "" : text;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Whether the job is over, however it ended. */
    public boolean over() {
        return this.state != STATE_RUNNING;
    }

    @Override
    public CustomPacketPayload.Type<SetupProgressPayload> type() {
        return TYPE;
    }
}
