/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: change one setting on the computer at {@code hostPos}. The key routes to the
 * computer name, the network share, or a value owned by the settings store; the server clamps the
 * value and replies with a refreshed {@link SettingsSnapshotPayload}.
 *
 * @param hostPos the computer's position
 * @param key     the setting key (e.g. {@code name}, {@code netshare}, {@code clock}, {@code accent})
 * @param value   the raw value
 */
public record SetSettingPayload(BlockPos hostPos, String key, String value) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetSettingPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "set_setting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSettingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetSettingPayload::hostPos,
                    ByteBufCodecs.stringUtf8(48), SetSettingPayload::key,
                    ByteBufCodecs.stringUtf8(128), SetSettingPayload::value,
                    SetSettingPayload::new);

    @Override
    public CustomPacketPayload.Type<SetSettingPayload> type() {
        return TYPE;
    }
}
