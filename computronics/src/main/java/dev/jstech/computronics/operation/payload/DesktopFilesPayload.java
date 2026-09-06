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
                                  List<WireIconCell> iconCells, Prefs prefs) implements CustomPacketPayload {

    public static final int MAX_FILES = 256;
    public static final int MAX_PROGRAMS = 16;
    public static final int MAX_ICON_CELLS = 256;

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

    public static final StreamCodec<RegistryFriendlyByteBuf, DesktopFilesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    DiskFilesPayload.WireFile.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES)),
                    DesktopFilesPayload::files,
                    ByteBufCodecs.stringUtf8(48), DesktopFilesPayload::wallpaper,
                    ByteBufCodecs.stringUtf8(48), DesktopFilesPayload::computerName,
                    ByteBufCodecs.stringUtf8(32).apply(ByteBufCodecs.list(MAX_PROGRAMS)),
                    DesktopFilesPayload::programs,
                    WireIconCell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ICON_CELLS)),
                    DesktopFilesPayload::iconCells,
                    Prefs.STREAM_CODEC, DesktopFilesPayload::prefs,
                    DesktopFilesPayload::new);

    @Override
    public CustomPacketPayload.Type<DesktopFilesPayload> type() {
        return TYPE;
    }
}
