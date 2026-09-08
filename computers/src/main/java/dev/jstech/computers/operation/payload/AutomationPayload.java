/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the Automation Manager's view. It reports whether a job-running engine (the Automation
 * Engine or the IQL Engine) is active on the network's Mainframe, a short label for it, and every saved job
 * with a friendly type, its trigger, and whether it is paused. Sent in reply to
 * {@link RequestAutomationPayload}.
 */
public record AutomationPayload(boolean engineOnline, String engineLabel, List<JobRow> jobs,
                               List<String> iqlFiles) implements CustomPacketPayload {

    public static final int MAX_JOBS = 64;
    public static final int MAX_FILES = 64;

    /** One saved job as the list shows it: name, a friendly type, its trigger summary, and paused state. */
    public record JobRow(String name, String type, String trigger, boolean paused) {
        public static final StreamCodec<RegistryFriendlyByteBuf, JobRow> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, JobRow::name,
                        ByteBufCodecs.STRING_UTF8, JobRow::type,
                        ByteBufCodecs.STRING_UTF8, JobRow::trigger,
                        ByteBufCodecs.BOOL, JobRow::paused,
                        JobRow::new);
    }

    public static final CustomPacketPayload.Type<AutomationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "automation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutomationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AutomationPayload::engineOnline,
                    ByteBufCodecs.stringUtf8(64), AutomationPayload::engineLabel,
                    JobRow.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_JOBS)), AutomationPayload::jobs,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_FILES)), AutomationPayload::iqlFiles,
                    AutomationPayload::new);

    @Override
    public CustomPacketPayload.Type<AutomationPayload> type() {
        return TYPE;
    }
}
