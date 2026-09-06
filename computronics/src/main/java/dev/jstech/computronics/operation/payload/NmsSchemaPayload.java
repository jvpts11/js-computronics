/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client when the Network Management Studio opens: a snapshot of the live network for the Object
 * Explorer tree. {@code networkLabel} names the network, {@code servers} lists the real server labels, and
 * {@code itemTypes}/{@code operations} are live counts. {@code engine} carries the IQL Engine's state and
 * its saved objects (views/procedures/jobs by name), so the tree shows the real catalog instead of mock
 * examples. The column schema itself is fixed on the client; only these values vary.
 */
public record NmsSchemaPayload(String networkLabel, List<String> servers, int itemTypes, int operations,
                               EngineSnapshot engine) implements CustomPacketPayload {

    public static final int MAX_SERVERS = 128;
    public static final int MAX_OBJECTS = 256;

    public static final CustomPacketPayload.Type<NmsSchemaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "nms_schema"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NmsSchemaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), NmsSchemaPayload::networkLabel,
                    ByteBufCodecs.stringUtf8(48).apply(ByteBufCodecs.list(MAX_SERVERS)), NmsSchemaPayload::servers,
                    ByteBufCodecs.VAR_INT, NmsSchemaPayload::itemTypes,
                    ByteBufCodecs.VAR_INT, NmsSchemaPayload::operations,
                    EngineSnapshot.STREAM_CODEC, NmsSchemaPayload::engine,
                    NmsSchemaPayload::new);

    @Override
    public CustomPacketPayload.Type<NmsSchemaPayload> type() {
        return TYPE;
    }

    /** The IQL Engine's state, saved objects (for the Object Explorer's Engine branch), and the persisted editor script (restored into the studio on open). */
    public record EngineSnapshot(String state, List<String> views, List<String> procedures, List<String> jobs,
                                 String script) {

        public static final int MAX_SCRIPT = 8192;

        public static final StreamCodec<RegistryFriendlyByteBuf, EngineSnapshot> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(32), EngineSnapshot::state,
                        ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_OBJECTS)), EngineSnapshot::views,
                        ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_OBJECTS)),
                        EngineSnapshot::procedures,
                        ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(MAX_OBJECTS)), EngineSnapshot::jobs,
                        ByteBufCodecs.stringUtf8(MAX_SCRIPT), EngineSnapshot::script,
                        EngineSnapshot::new);

        public static EngineSnapshot offline() {
            return new EngineSnapshot("not installed", List.of(), List.of(), List.of(), "");
        }
    }
}
