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
 * Server to client: the full state the Settings app draws: the editable per-computer knobs, the
 * read-only hardware/OS specs (System &amp; Display pages), the installed programs (Programs page),
 * the per-disk usage (Storage page) and the memory ledger (what holds RAM, for the System Monitor).
 * Sent in reply to {@link RequestSettingsPayload} and after every {@link SetSettingPayload}.
 *
 * <p>The stream codec is written by hand because the payload has more fields than
 * {@code StreamCodec.composite} accepts.
 */
public record SettingsSnapshotPayload(
        BlockPos hostPos,
        String wallpaper,
        String computerName,
        int accent,
        boolean clock12h,
        int guiScale,
        int brightness,
        String saveDrive,
        boolean removableAutoOpen,
        String themePreset,
        boolean taskbarCentered,
        boolean darkMode,
        int netshare,
        String cpuLabel,
        int cpuMhz,
        int ramMb,
        int vramMb,
        String osLabel,
        String platform,
        List<String> installed,
        List<DiskUse> disks,
        int ramUsedMb,
        List<RamUse> ramUses
) implements CustomPacketPayload {

    /** One disk's usage for the Storage page. */
    public record DiskUse(String label, long capMb, long usedMb, boolean system) {}

    /** One holder of RAM for the System Monitor: what it is called, its megabytes and its kind's name. */
    /**
     * One thing holding memory: what to call it, how many megabytes, what kind it is, and the number it
     * answers to if it is something that can be ended.
     *
     * <p>A window is ended by its name, but two scripts can be started from the same file and only the
     * number tells them apart, so the number travels for those and is 0 for everything else.
     */
    public record RamUse(String label, int mb, String kind, int id) {}

    public static final int MAX = 64;

    public static final CustomPacketPayload.Type<SettingsSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "settings_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(SettingsSnapshotPayload::encode, SettingsSnapshotPayload::decode);

    @Override
    public CustomPacketPayload.Type<SettingsSnapshotPayload> type() {
        return TYPE;
    }

    private static void encode(final RegistryFriendlyByteBuf buf, final SettingsSnapshotPayload p) {
        buf.writeBlockPos(p.hostPos);
        buf.writeUtf(p.wallpaper, 48);
        buf.writeUtf(p.computerName, 48);
        buf.writeInt(p.accent);
        buf.writeBoolean(p.clock12h);
        buf.writeVarInt(p.guiScale);
        buf.writeVarInt(p.brightness);
        buf.writeUtf(p.saveDrive, 4);
        buf.writeBoolean(p.removableAutoOpen);
        buf.writeUtf(p.themePreset, 32);
        buf.writeBoolean(p.taskbarCentered);
        buf.writeBoolean(p.darkMode);
        buf.writeVarInt(p.netshare);
        buf.writeUtf(p.cpuLabel, 64);
        buf.writeVarInt(p.cpuMhz);
        buf.writeVarInt(p.ramMb);
        buf.writeVarInt(p.vramMb);
        buf.writeUtf(p.osLabel, 48);
        buf.writeUtf(p.platform, 24);
        buf.writeVarInt(Math.min(p.installed.size(), MAX));
        for (int i = 0; i < p.installed.size() && i < MAX; i++) {
            buf.writeUtf(p.installed.get(i), 96);
        }
        buf.writeVarInt(Math.min(p.disks.size(), MAX));
        for (int i = 0; i < p.disks.size() && i < MAX; i++) {
            final DiskUse d = p.disks.get(i);
            buf.writeUtf(d.label(), 48);
            buf.writeVarLong(d.capMb());
            buf.writeVarLong(d.usedMb());
            buf.writeBoolean(d.system());
        }
        buf.writeVarInt(p.ramUsedMb);
        buf.writeVarInt(Math.min(p.ramUses.size(), MAX));
        for (int i = 0; i < p.ramUses.size() && i < MAX; i++) {
            final RamUse r = p.ramUses.get(i);
            buf.writeUtf(r.label(), 48);
            buf.writeVarInt(r.mb());
            buf.writeUtf(r.kind(), 16);
            buf.writeVarInt(r.id());
        }
    }

    private static SettingsSnapshotPayload decode(final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final String wallpaper = buf.readUtf(48);
        final String computerName = buf.readUtf(48);
        final int accent = buf.readInt();
        final boolean clock12h = buf.readBoolean();
        final int guiScale = buf.readVarInt();
        final int brightness = buf.readVarInt();
        final String saveDrive = buf.readUtf(4);
        final boolean removableAutoOpen = buf.readBoolean();
        final String themePreset = buf.readUtf(32);
        final boolean taskbarCentered = buf.readBoolean();
        final boolean darkMode = buf.readBoolean();
        final int netshare = buf.readVarInt();
        final String cpuLabel = buf.readUtf(64);
        final int cpuMhz = buf.readVarInt();
        final int ramMb = buf.readVarInt();
        final int vramMb = buf.readVarInt();
        final String osLabel = buf.readUtf(48);
        final String platform = buf.readUtf(24);
        final int programCount = Math.min(buf.readVarInt(), MAX);
        final List<String> installed = new ArrayList<>(programCount);
        for (int i = 0; i < programCount; i++) {
            installed.add(buf.readUtf(96));
        }
        final int diskCount = Math.min(buf.readVarInt(), MAX);
        final List<DiskUse> disks = new ArrayList<>(diskCount);
        for (int i = 0; i < diskCount; i++) {
            disks.add(new DiskUse(buf.readUtf(48), buf.readVarLong(), buf.readVarLong(), buf.readBoolean()));
        }
        final int ramUsedMb = buf.readVarInt();
        final int ramUseCount = Math.min(buf.readVarInt(), MAX);
        final List<RamUse> ramUses = new ArrayList<>(ramUseCount);
        for (int i = 0; i < ramUseCount; i++) {
            ramUses.add(new RamUse(buf.readUtf(48), buf.readVarInt(), buf.readUtf(16), buf.readVarInt()));
        }
        return new SettingsSnapshotPayload(pos, wallpaper, computerName, accent, clock12h, guiScale, brightness,
                saveDrive, removableAutoOpen, themePreset, taskbarCentered, darkMode, netshare, cpuLabel, cpuMhz,
                ramMb, vramMb, osLabel, platform, installed, disks, ramUsedMb, ramUses);
    }
}
