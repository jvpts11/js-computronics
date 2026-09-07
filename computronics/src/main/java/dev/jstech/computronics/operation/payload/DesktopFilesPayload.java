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
 * Server to client: the listing of the desktop folder for the Frames desktop background. Each entry
 * is a {@link DiskFilesPayload.WireFile} (path, extension, weight, read-only and directory flags),
 * rendered as an icon on the desktop. Reuses the Files app's wire type so there is one file model.
 *
 * <p>{@code iconCells} carries every free-positioned desktop icon's pinned grid cell, so the client
 * places those icons exactly where the player dropped them; an icon with no entry flows into the next
 * free auto-layout cell.
 */
public record DesktopFilesPayload(List<DiskFilesPayload.WireFile> files, String wallpaper,
                                  String computerName, List<String> programs,
                                  List<WireIconCell> iconCells, Prefs prefs,
                                  List<WireCommunity> community) implements CustomPacketPayload {

    public static final int MAX_FILES = 256;
    public static final int MAX_PROGRAMS = 16;
    public static final int MAX_ICON_CELLS = 256;

    /** How many player-written programs one desktop shows; the same cap the Mirror's shelf has. */
    public static final int MAX_COMMUNITY = 64;

    /**
     * A program the player installed from the Mirror, as the desktop needs it.
     *
     * <p>Only what it takes to put an icon on the desktop and start the thing: what it is called, which
     * icon it asked for, and the listing to run. Everything else about it stays on the server.
     */
    public record WireCommunity(String name, String icon, String entry) {
    }

    /**
     * The desktop-relevant per-computer settings the chrome applies: accent override, brightness, clock,
     * whether the taskbar app strip is centered (a Frames 11 look) or left-aligned, and dark mode.
     */
    public record Prefs(int accent, int brightness, boolean clock12h, boolean taskbarCentered, boolean darkMode) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Prefs> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, Prefs::accent,
                        ByteBufCodecs.VAR_INT, Prefs::brightness,
                        ByteBufCodecs.BOOL, Prefs::clock12h,
                        ByteBufCodecs.BOOL, Prefs::taskbarCentered,
                        ByteBufCodecs.BOOL, Prefs::darkMode,
                        Prefs::new);
    }

    /** One pinned desktop icon: its stable id ({@code app:<label>} / {@code file:<name>}) and packed grid cell. */
    public record WireIconCell(String key, int cell) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireIconCell> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(80), WireIconCell::key,
                        ByteBufCodecs.INT, WireIconCell::cell,
                        WireIconCell::new);
    }

    public static final CustomPacketPayload.Type<DesktopFilesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "desktop_files"));

    // Written out by hand: composite takes six pairs and this carries seven things. The alternative was
    // to bundle two of them into a record nobody else wants, which would have cost a reader more than
    // these two short methods do.
    public static final StreamCodec<RegistryFriendlyByteBuf, DesktopFilesPayload> STREAM_CODEC =
            StreamCodec.of(DesktopFilesPayload::encode, DesktopFilesPayload::decode);

    private static void encode(final RegistryFriendlyByteBuf buf, final DesktopFilesPayload payload) {
        DiskFilesPayload.WireFile.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES))
                .encode(buf, payload.files);
        buf.writeUtf(payload.wallpaper, 48);
        buf.writeUtf(payload.computerName, 48);
        ByteBufCodecs.stringUtf8(32).apply(ByteBufCodecs.list(MAX_PROGRAMS)).encode(buf, payload.programs);
        WireIconCell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ICON_CELLS)).encode(buf, payload.iconCells);
        Prefs.STREAM_CODEC.encode(buf, payload.prefs);
        buf.writeVarInt(Math.min(payload.community.size(), MAX_COMMUNITY));
        for (int i = 0; i < payload.community.size() && i < MAX_COMMUNITY; i++) {
            final WireCommunity one = payload.community.get(i);
            buf.writeUtf(one.name(), 32);
            buf.writeUtf(one.icon(), 16);
            buf.writeUtf(one.entry(), 128);
        }
    }

    private static DesktopFilesPayload decode(final RegistryFriendlyByteBuf buf) {
        final List<DiskFilesPayload.WireFile> files =
                DiskFilesPayload.WireFile.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES)).decode(buf);
        final String wallpaper = buf.readUtf(48);
        final String computerName = buf.readUtf(48);
        final List<String> programs =
                ByteBufCodecs.stringUtf8(32).apply(ByteBufCodecs.list(MAX_PROGRAMS)).decode(buf);
        final List<WireIconCell> cells =
                WireIconCell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ICON_CELLS)).decode(buf);
        final Prefs prefs = Prefs.STREAM_CODEC.decode(buf);
        final int count = Math.min(buf.readVarInt(), MAX_COMMUNITY);
        final List<WireCommunity> community = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            community.add(new WireCommunity(buf.readUtf(32), buf.readUtf(16), buf.readUtf(128)));
        }
        return new DesktopFilesPayload(files, wallpaper, computerName, programs, cells, prefs, community);
    }

    @Override
    public CustomPacketPayload.Type<DesktopFilesPayload> type() {
        return TYPE;
    }
}
