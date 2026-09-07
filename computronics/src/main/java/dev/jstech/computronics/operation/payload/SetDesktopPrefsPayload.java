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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: store the desktop personalization for the computer at {@code hostPos}: the
 * chosen wallpaper id ({@code ""} keeps the OS default) and the computer name ({@code ""} clears it).
 * Persisted on the computer so it survives a reload and shows on every monitor.
 */
public record SetDesktopPrefsPayload(BlockPos hostPos, String wallpaper, String computerName)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetDesktopPrefsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "set_desktop_prefs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDesktopPrefsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetDesktopPrefsPayload::hostPos,
                    ByteBufCodecs.stringUtf8(48), SetDesktopPrefsPayload::wallpaper,
                    ByteBufCodecs.stringUtf8(48), SetDesktopPrefsPayload::computerName,
                    SetDesktopPrefsPayload::new);

    @Override
    public CustomPacketPayload.Type<SetDesktopPrefsPayload> type() {
        return TYPE;
    }
}
