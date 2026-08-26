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
 * Server to client: the contents of the "This PC" app — the disks installed in the computer, the
 * removable media in its linked drives, and the ids of the programs installed on it.
 */
public record ThisPcPayload(List<WireDisk> disks, List<WireMedia> media, List<String> installedPrograms)
        implements CustomPacketPayload {

    public static final int MAX = 64;

    public static final CustomPacketPayload.Type<ThisPcPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "this_pc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThisPcPayload> STREAM_CODEC =
            StreamCodec.composite(
                    WireDisk.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), ThisPcPayload::disks,
                    WireMedia.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), ThisPcPayload::media,
                    ByteBufCodecs.stringUtf8(96).apply(ByteBufCodecs.list(MAX)), ThisPcPayload::installedPrograms,
                    ThisPcPayload::new);

    @Override
    public CustomPacketPayload.Type<ThisPcPayload> type() {
        return TYPE;
    }

    /**
     * One disk installed in the computer.
     *
     * @param slot       the disk slot index (0-based within the disk range)
     * @param label      the disk's display name
     * @param capItems   total capacity in item-equivalents (1 item = 4 MB)
     * @param usedItems  used space in item-equivalents (storage + files + OS footprint)
     * @param system     true if this is the bootable system disk
     * @param osPath     the installed OS path (e.g. {@code panes_xp}), or empty for a data disk
     */
    public record WireDisk(int slot, String label, long capItems, long usedItems, boolean system,
                           String osPath) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireDisk> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, WireDisk::slot,
                        ByteBufCodecs.stringUtf8(96), WireDisk::label,
                        ByteBufCodecs.VAR_LONG, WireDisk::capItems,
                        ByteBufCodecs.VAR_LONG, WireDisk::usedItems,
                        ByteBufCodecs.BOOL, WireDisk::system,
                        ByteBufCodecs.stringUtf8(64), WireDisk::osPath,
                        WireDisk::new);
    }

    /**
     * One removable medium present in a drive linked to the computer.
     *
     * @param readerPos   the drive block's packed position
     * @param drive       the drive type name (e.g. CD_DRIVE)
     * @param mediaName   the medium's display name
     * @param kind        the media kind (OS_INSTALL / PROGRAM_INSTALL / DATA / empty when none)
     * @param payloadPath the OS/program id carried, or empty
     * @param installable true if this is a PROGRAM_INSTALL medium whose program is not yet installed
     */
    public record WireMedia(long readerPos, String drive, String mediaName, String kind,
                            String payloadPath, boolean installable) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireMedia> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, WireMedia::readerPos,
                        ByteBufCodecs.stringUtf8(48), WireMedia::drive,
                        ByteBufCodecs.stringUtf8(96), WireMedia::mediaName,
                        ByteBufCodecs.stringUtf8(24), WireMedia::kind,
                        ByteBufCodecs.stringUtf8(96), WireMedia::payloadPath,
                        ByteBufCodecs.BOOL, WireMedia::installable,
                        WireMedia::new);
    }
}
