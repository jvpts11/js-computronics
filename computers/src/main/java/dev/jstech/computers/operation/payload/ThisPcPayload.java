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
 * Server to client: what the "This PC" window shows. The machine itself (name, kind, era, system,
 * network, hardware), the disks installed in it, the removable media in its linked drives, and the
 * ids of the programs installed on it. A client copy of a computer knows none of this on its own.
 */
public record ThisPcPayload(WireMachine machine, List<WireDisk> disks, List<WireMedia> media,
                            List<String> installedPrograms)
        implements CustomPacketPayload {

    public static final int MAX = 64;

    public static final CustomPacketPayload.Type<ThisPcPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "this_pc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThisPcPayload> STREAM_CODEC =
            StreamCodec.composite(
                    WireMachine.STREAM_CODEC, ThisPcPayload::machine,
                    WireDisk.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), ThisPcPayload::disks,
                    WireMedia.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), ThisPcPayload::media,
                    ByteBufCodecs.stringUtf8(96).apply(ByteBufCodecs.list(MAX)), ThisPcPayload::installedPrograms,
                    ThisPcPayload::new);

    @Override
    public CustomPacketPayload.Type<ThisPcPayload> type() {
        return TYPE;
    }

    /**
     * The machine card: what this computer is before any list.
     *
     * @param name         the player's name for it, or empty
     * @param kind         what it is ("Personal Computer", "Mainframe", ...)
     * @param era          the hardware era's label
     * @param osLabel      the installed system's name, or empty
     * @param osYear       the year that system shipped, 0 without one
     * @param networkLabel the network it sits on, or empty
     * @param boardLabel   the motherboard's name, or empty
     * @param cpuLabel     the processor's name with its clock, or empty
     * @param cpuCount     how many processors are seated
     * @param ramMb        installed memory
     * @param vramMb       installed video memory
     * @param gpuCount     how many graphics cards are seated
     * @param psuLabel     the power supply's name, or empty
     * @param buildValid   whether the parts make a machine that comes up
     * @param peripherals  the linked peripherals, already joined for display
     */
    public record WireMachine(String name, String kind, String era, String osLabel, int osYear,
                              String networkLabel, String boardLabel, String cpuLabel, int cpuCount,
                              int ramMb, int vramMb, int gpuCount, String psuLabel, boolean buildValid,
                              String peripherals) {

        public static final WireMachine EMPTY = new WireMachine("", "", "", "", 0, "", "", "", 0, 0, 0, 0, "",
                false, "");

        // Written by hand: composite() tops out at six pairs, and the card carries fifteen fields.
        public static final StreamCodec<RegistryFriendlyByteBuf, WireMachine> STREAM_CODEC =
                StreamCodec.of((buf, m) -> {
                    buf.writeUtf(m.name(), 96);
                    buf.writeUtf(m.kind(), 48);
                    buf.writeUtf(m.era(), 32);
                    buf.writeUtf(m.osLabel(), 64);
                    buf.writeVarInt(m.osYear());
                    buf.writeUtf(m.networkLabel(), 64);
                    buf.writeUtf(m.boardLabel(), 96);
                    buf.writeUtf(m.cpuLabel(), 96);
                    buf.writeVarInt(m.cpuCount());
                    buf.writeVarInt(m.ramMb());
                    buf.writeVarInt(m.vramMb());
                    buf.writeVarInt(m.gpuCount());
                    buf.writeUtf(m.psuLabel(), 96);
                    buf.writeBoolean(m.buildValid());
                    buf.writeUtf(m.peripherals(), 256);
                }, buf -> new WireMachine(buf.readUtf(96), buf.readUtf(48), buf.readUtf(32), buf.readUtf(64),
                        buf.readVarInt(), buf.readUtf(64), buf.readUtf(96), buf.readUtf(96), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(96), buf.readBoolean(),
                        buf.readUtf(256)));
    }

    /**
     * One disk installed in the computer.
     *
     * @param slot       the disk slot index (0-based within the disk range)
     * @param label      the disk's display name
     * @param capItems   total capacity in item-equivalents (1 item = 4 MB)
     * @param usedItems  used space in item-equivalents (storage + files + OS footprint)
     * @param system     true if this is the bootable system disk
     * @param osPath     the installed OS path (e.g. {@code frames_xp}), or empty for a data disk
     * @param osItems    the share the installed system occupies
     * @param storeItems the share stored items occupy
     * @param fileItems  the share files and installed programs occupy
     */
    public record WireDisk(int slot, String label, long capItems, long usedItems, boolean system,
                           String osPath, long osItems, long storeItems, long fileItems) {

        // Written by hand: composite() tops out at six pairs, and a disk now carries nine fields.
        public static final StreamCodec<RegistryFriendlyByteBuf, WireDisk> STREAM_CODEC =
                StreamCodec.of((buf, d) -> {
                    buf.writeVarInt(d.slot());
                    buf.writeUtf(d.label(), 96);
                    buf.writeVarLong(d.capItems());
                    buf.writeVarLong(d.usedItems());
                    buf.writeBoolean(d.system());
                    buf.writeUtf(d.osPath(), 64);
                    buf.writeVarLong(d.osItems());
                    buf.writeVarLong(d.storeItems());
                    buf.writeVarLong(d.fileItems());
                }, buf -> new WireDisk(buf.readVarInt(), buf.readUtf(96), buf.readVarLong(),
                        buf.readVarLong(), buf.readBoolean(), buf.readUtf(64),
                        buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));

        /** Free space in item-equivalents. */
        public long freeItems() {
            return Math.max(0L, capItems - usedItems);
        }
    }

    /**
     * One drive linked to the computer, with whatever medium is in it.
     *
     * @param readerPos   the drive block's packed position
     * @param drive       the drive type name (e.g. CD_DRIVE)
     * @param mediaName   the medium's display name, or empty for an empty drive
     * @param kind        the media kind (OS_INSTALL / PROGRAM_INSTALL / DATA / empty when none)
     * @param payloadPath the OS/program id carried, or empty
     * @param installable true if this is an installer whose software is not yet installed here
     * @param payloadName what the medium installs, by name, or empty
     * @param payloadYear the year that software shipped, 0 for none
     * @param packageId   the id a package manager installs it by, or empty
     * @param needs       its requirements, already joined for display
     * @param stored      what a data medium holds, in item-equivalents
     * @param blocksAway  how far the drive is from the computer, in blocks
     */
    public record WireMedia(long readerPos, String drive, String mediaName, String kind,
                            String payloadPath, boolean installable, String payloadName, int payloadYear,
                            String packageId, String needs, long stored, int blocksAway) {

        // Written by hand: composite() tops out at six pairs, and a drive now carries twelve fields.
        public static final StreamCodec<RegistryFriendlyByteBuf, WireMedia> STREAM_CODEC =
                StreamCodec.of((buf, m) -> {
                    buf.writeVarLong(m.readerPos());
                    buf.writeUtf(m.drive(), 48);
                    buf.writeUtf(m.mediaName(), 96);
                    buf.writeUtf(m.kind(), 24);
                    buf.writeUtf(m.payloadPath(), 96);
                    buf.writeBoolean(m.installable());
                    buf.writeUtf(m.payloadName(), 96);
                    buf.writeVarInt(m.payloadYear());
                    buf.writeUtf(m.packageId(), 64);
                    buf.writeUtf(m.needs(), 192);
                    buf.writeVarLong(m.stored());
                    buf.writeVarInt(m.blocksAway());
                }, buf -> new WireMedia(buf.readVarLong(), buf.readUtf(48), buf.readUtf(96), buf.readUtf(24),
                        buf.readUtf(96), buf.readBoolean(), buf.readUtf(96), buf.readVarInt(), buf.readUtf(64),
                        buf.readUtf(192), buf.readVarLong(), buf.readVarInt()));

        /** Whether the drive holds anything at all. */
        public boolean loaded() {
            return !mediaName.isEmpty();
        }
    }
}
