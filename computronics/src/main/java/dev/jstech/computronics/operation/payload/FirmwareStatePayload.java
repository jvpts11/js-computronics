/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: everything the firmware boot manager shows for one computer — the boot entries (each
 * disk, with or without a system; each linked drive holding a bootable medium), the preferred boot disk,
 * and the hardware summary. Sent in reply to {@link RequestFirmwareStatePayload} and after every
 * {@link FirmwareActionPayload} that changes the state.
 */
public record FirmwareStatePayload(
        BlockPos hostPos,
        int eraOrdinal,
        String cpuLabel,
        int cpuMhz,
        int ramMb,
        int bootSlot,
        int installTargetSlot,
        List<Entry> entries,
        RaidInfo raid
) implements CustomPacketPayload {

    /**
     * The storage controller the firmware found, if any. A real RAID controller is configured from
     * its own setup at power-on rather than from inside the running system, so the firmware is where
     * this lives.
     *
     * @param present    whether a controller is fitted at all (false hides the storage page)
     * @param mode       the array mode ordinal currently configured
     * @param members    how many drives the array was formed with (0 when unconfigured)
     * @param drives     how many member drives are present right now
     * @param capacities usable capacity in items for each mode, indexed by mode ordinal
     */
    public record RaidInfo(boolean present, int mode, int members, int drives, List<Long> capacities) {

        public static final RaidInfo ABSENT = new RaidInfo(false, 0, 0, 0, List.of());

        /** Whether the array is missing members but still serving. */
        public boolean degraded() {
            return members > drives && drives > 0;
        }
    }

    public static final int KIND_DISK = 0;
    public static final int KIND_MEDIA = 1;
    public static final int MAX_ENTRIES = 32;

    /**
     * One boot entry.
     *
     * @param kind        {@link #KIND_DISK} (an installed disk) or {@link #KIND_MEDIA} (a linked drive's medium)
     * @param ref         the disk slot index, or the media reader's packed block position
     * @param osId        the OS on it ({@code ""} when the disk has no system / the medium is not an installer)
     * @param label       the display label (OS name, or "no system" / the medium's name)
     * @param detail      a second line: the disk/medium size and device
     * @param bootable    whether this entry can be booted (a disk with an OS; an OS-installer medium whose OS
     *                    passes the hardware era gate)
     * @param installMode the OS's install mode ordinal for a medium (guided / live manual / source), else -1
     */
    public record Entry(int kind, long ref, String osId, String label, String detail, boolean bootable,
                        int installMode) {
    }

    public static final CustomPacketPayload.Type<FirmwareStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "firmware_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FirmwareStatePayload> STREAM_CODEC =
            StreamCodec.of(FirmwareStatePayload::encode, FirmwareStatePayload::decode);

    @Override
    public CustomPacketPayload.Type<FirmwareStatePayload> type() {
        return TYPE;
    }

    private static void encode(final RegistryFriendlyByteBuf buf, final FirmwareStatePayload p) {
        buf.writeBlockPos(p.hostPos);
        buf.writeVarInt(p.eraOrdinal);
        buf.writeUtf(p.cpuLabel, 48);
        buf.writeVarInt(p.cpuMhz);
        buf.writeVarInt(p.ramMb);
        buf.writeVarInt(p.bootSlot);
        buf.writeVarInt(p.installTargetSlot);
        buf.writeVarInt(Math.min(p.entries.size(), MAX_ENTRIES));
        for (int i = 0; i < p.entries.size() && i < MAX_ENTRIES; i++) {
            final Entry e = p.entries.get(i);
            buf.writeVarInt(e.kind());
            buf.writeVarLong(e.ref());
            buf.writeUtf(e.osId(), 64);
            buf.writeUtf(e.label(), 48);
            buf.writeUtf(e.detail(), 48);
            buf.writeBoolean(e.bootable());
            buf.writeVarInt(e.installMode());
        }
        final RaidInfo raid = p.raid == null ? RaidInfo.ABSENT : p.raid;
        buf.writeBoolean(raid.present());
        if (raid.present()) {
            buf.writeVarInt(raid.mode());
            buf.writeVarInt(raid.members());
            buf.writeVarInt(raid.drives());
            buf.writeVarInt(raid.capacities().size());
            for (final long capacity : raid.capacities()) {
                buf.writeVarLong(capacity);
            }
        }
    }

    private static FirmwareStatePayload decode(final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final int era = buf.readVarInt();
        final String cpuLabel = buf.readUtf(48);
        final int cpuMhz = buf.readVarInt();
        final int ramMb = buf.readVarInt();
        final int bootSlot = buf.readVarInt();
        final int target = buf.readVarInt();
        final int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
        final List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readVarLong(), buf.readUtf(64), buf.readUtf(48),
                    buf.readUtf(48), buf.readBoolean(), buf.readVarInt()));
        }
        RaidInfo raid = RaidInfo.ABSENT;
        if (buf.readBoolean()) {
            final int mode = buf.readVarInt();
            final int members = buf.readVarInt();
            final int drives = buf.readVarInt();
            final int capacityCount = Math.min(buf.readVarInt(), 8);
            final List<Long> capacities = new ArrayList<>(capacityCount);
            for (int i = 0; i < capacityCount; i++) {
                capacities.add(buf.readVarLong());
            }
            raid = new RaidInfo(true, mode, members, drives, capacities);
        }
        return new FirmwareStatePayload(pos, era, cpuLabel, cpuMhz, ramMb, bootSlot, target, entries, raid);
    }
}
