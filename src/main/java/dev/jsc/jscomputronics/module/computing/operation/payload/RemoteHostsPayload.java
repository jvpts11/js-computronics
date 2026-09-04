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
 * Server to client: the machines Remote Control can take over from this computer — every other
 * computer on the network, with what it runs and whether it is up.
 */
public record RemoteHostsPayload(List<Entry> hosts) implements CustomPacketPayload {

    /** One remotely reachable machine: where it is, what it is, and what it is running. */
    public record Entry(long pos, String hostname, String type, String os, boolean running) {
    }

    public static final int MAX_HOSTS = 64;

    public static final CustomPacketPayload.Type<RemoteHostsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "remote_hosts"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, Entry::pos,
                    ByteBufCodecs.STRING_UTF8, Entry::hostname,
                    ByteBufCodecs.STRING_UTF8, Entry::type,
                    ByteBufCodecs.STRING_UTF8, Entry::os,
                    ByteBufCodecs.BOOL, Entry::running,
                    Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteHostsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_HOSTS)), RemoteHostsPayload::hosts,
                    RemoteHostsPayload::new);

    @Override
    public CustomPacketPayload.Type<RemoteHostsPayload> type() {
        return TYPE;
    }
}
