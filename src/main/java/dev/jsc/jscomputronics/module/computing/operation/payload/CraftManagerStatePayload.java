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
 * Server to client: the current state of the Crafting Manager for an open Crafting Computer.
 *
 * <p>Contains the list of {@code .craft} files on the first removable medium linked to the
 * computer, the crafting patterns currently in the Recipe ROM (each annotated with whether a file
 * with the same name exists on the medium), and the machines the network's Crafting Switches offer
 * with their per-machine concurrency config (the Machines tab).
 *
 * @param mediaVolumeKey the {@code media:<readerPos>} key of the medium, or {@code ""} when none
 * @param mediaLabel     the display label of the medium (e.g. "Floppy (A:)"), or {@code ""} when none
 * @param mediaFiles     file names of {@code .craft} files on the medium (up to 64)
 * @param romEntries     recipe ROM entries (up to 50)
 * @param hasCard        true when the computer has a Crafting Card installed (actions are enabled)
 * @param status         a short status line for the manager
 * @param machines       the routed machines with their concurrency config (the Machines tab)
 */
public record CraftManagerStatePayload(
        String mediaVolumeKey,
        String mediaLabel,
        List<String> mediaFiles,
        List<WireRomEntry> romEntries,
        boolean hasCard,
        String status,
        List<WireMachine> machines) implements CustomPacketPayload {

    public static final int MAX_MEDIA_FILES = 64;
    public static final int MAX_ROM_ENTRIES = 50;
    public static final int MAX_MACHINES = 32;

    /** ROM indices at or above this base refer to machine recipes (processing/multi-stage), not bench patterns. */
    public static final int MACHINE_ROM_BASE = 100000;

    public static final CustomPacketPayload.Type<CraftManagerStatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "craft_manager_state"));

    /**
     * One entry in the Recipe ROM, sent to the client so the Crafting Manager can list what is
     * already loaded and cross-reference it against the medium's files.
     *
     * @param index   the zero-based index in the ROM list
     * @param name    the display name of the result item (e.g. "Diamond Sword")
     * @param inMedia true when a {@code .craft} file for this pattern already exists on the medium
     */
    public record WireRomEntry(int index, String name, boolean inMedia) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WireRomEntry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, WireRomEntry::index,
                        ByteBufCodecs.stringUtf8(64), WireRomEntry::name,
                        ByteBufCodecs.BOOL, WireRomEntry::inMedia,
                        WireRomEntry::new);
    }

    /**
     * One routed machine the Machines tab lists, with its per-machine concurrency config.
     *
     * @param key     the machine key the engine matches (its name when named, else its block registry id)
     * @param label   a human-readable label (the name, or the block id's path)
     * @param active  true when the machine is currently reachable/active on a switch face
     * @param maxJobs how many processing jobs may run on it at once
     * One physical machine on the network. {@code machineKey} is its per-machine config key (Paused/Feed are set
     * on it); {@code typeKey} is its machine type (Max Jobs is a per-type ceiling, and rows group by it);
     * {@code label} distinguishes it within the type (its switch face + position). {@code typeMaxJobs} is the
     * type's shared Max Jobs.
     */
    public record WireMachine(String machineKey, String typeKey, String label,
                              boolean locked, boolean feedMax, int typeMaxJobs) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WireMachine> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(48), WireMachine::machineKey,
                        ByteBufCodecs.stringUtf8(80), WireMachine::typeKey,
                        ByteBufCodecs.stringUtf8(64), WireMachine::label,
                        ByteBufCodecs.BOOL, WireMachine::locked,
                        ByteBufCodecs.BOOL, WireMachine::feedMax,
                        ByteBufCodecs.VAR_INT, WireMachine::typeMaxJobs,
                        WireMachine::new);
    }

    private record FileName(String value) {
        static final StreamCodec<RegistryFriendlyByteBuf, FileName> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(42), FileName::value,
                        FileName::new);
    }

    // Groups the medium fields so the top-level Wire stays within the 6-pair composite limit.
    private record MediaBlock(String key, String label, List<FileName> files) {
        static final StreamCodec<RegistryFriendlyByteBuf, MediaBlock> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(64), MediaBlock::key,
                        ByteBufCodecs.stringUtf8(64), MediaBlock::label,
                        FileName.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MEDIA_FILES)), MediaBlock::files,
                        MediaBlock::new);
    }

    // Wire record holds the serialized form; .map() converts to/from the flat public record.
    private record Wire(MediaBlock media, List<WireRomEntry> rom, boolean hasCard, String status,
                        List<WireMachine> machines) {
        static final StreamCodec<RegistryFriendlyByteBuf, Wire> STREAM_CODEC =
                StreamCodec.composite(
                        MediaBlock.STREAM_CODEC, Wire::media,
                        WireRomEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROM_ENTRIES)), Wire::rom,
                        ByteBufCodecs.BOOL, Wire::hasCard,
                        ByteBufCodecs.stringUtf8(96), Wire::status,
                        WireMachine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MACHINES)), Wire::machines,
                        Wire::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftManagerStatePayload> STREAM_CODEC =
            Wire.STREAM_CODEC.map(
                    w -> new CraftManagerStatePayload(
                            w.media().key(), w.media().label(),
                            w.media().files().stream().map(FileName::value).toList(),
                            w.rom(), w.hasCard(), w.status(), w.machines()),
                    p -> new Wire(
                            new MediaBlock(p.mediaVolumeKey(), p.mediaLabel(),
                                    p.mediaFiles().stream().map(FileName::new).toList()),
                            p.romEntries(), p.hasCard(), p.status(), p.machines()));

    @Override
    public CustomPacketPayload.Type<CraftManagerStatePayload> type() {
        return TYPE;
    }
}
