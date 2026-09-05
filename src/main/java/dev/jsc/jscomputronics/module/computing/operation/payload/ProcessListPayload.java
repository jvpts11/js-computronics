/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client when the terminal's Processes tab opens: the processes running on THIS computer (the
 * host the terminal is attached to), for the task manager. On a Mainframe that is the IQL Engine service and
 * each of its jobs; a computer with no service sends an empty list. Each line carries its kind (so the tab
 * picks the icon and the right actions), its name, its state and a short detail (uptime, the job's trigger,
 * or its condition).
 */
public record ProcessListPayload(List<ProcessLine> processes) implements CustomPacketPayload {

    public static final int MAX = 64;

    /** A service process (a background daemon, e.g. the IQL Engine): actions are Start/Stop/Restart. */
    public static final int KIND_SERVICE = 0;
    /** A job process (a scheduled IQL job): actions are End (pause) / Restart (re-arm). */
    public static final int KIND_JOB = 1;

    public static final CustomPacketPayload.Type<ProcessListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "process_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProcessListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ProcessLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), ProcessListPayload::processes,
                    ProcessListPayload::new);

    @Override
    public CustomPacketPayload.Type<ProcessListPayload> type() {
        return TYPE;
    }

    /** One process: its kind, display name, current state, and a short detail line. */
    public record ProcessLine(int kind, String name, String state, String detail) {

        public static final StreamCodec<RegistryFriendlyByteBuf, ProcessLine> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, ProcessLine::kind,
                        ByteBufCodecs.stringUtf8(48), ProcessLine::name,
                        ByteBufCodecs.stringUtf8(24), ProcessLine::state,
                        ByteBufCodecs.stringUtf8(64), ProcessLine::detail,
                        ProcessLine::new);
    }
}
