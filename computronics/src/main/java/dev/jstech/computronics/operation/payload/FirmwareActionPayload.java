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
 * Client to server: one action taken in the firmware boot manager.
 *
 * @param hostPos    the computer
 * @param monitorPos the monitor the firmware was opened from (the booted OS opens on it)
 * @param action     one of the {@code ACTION_*} constants
 * @param ref        the entry reference: a disk slot index (boot / boot order) or a media reader's packed
 *                   position (install / boot media); {@code -1} = any linked installer medium
 * @param target     the disk slot to install onto, or {@code -1} for the firmware's default target
 */
public record FirmwareActionPayload(BlockPos hostPos, BlockPos monitorPos, int action, long ref, int target)
        implements CustomPacketPayload {

    /** Boot the OS on disk slot {@code ref} (also makes it the preferred boot disk). */
    public static final int ACTION_BOOT_DISK = 0;
    /** Install the OS from the medium in reader {@code ref} (or any, when -1) onto disk {@code target}. */
    public static final int ACTION_INSTALL = 1;
    /** Make disk slot {@code ref} the preferred boot disk without booting. */
    public static final int ACTION_SET_BOOT = 2;
    /** Boot the medium in reader {@code ref}: a guided installer installs and boots; a live medium opens its shell. */
    public static final int ACTION_BOOT_MEDIA = 3;
    /** Format disk slot {@code ref}: erase its system, files and storage (the client asks twice first). */
    public static final int ACTION_FORMAT = 4;
    /**
     * Set the storage controller's array mode to the {@code ref}-th mode. Configuring an array from
     * the firmware — not from inside a running system — is how a real controller works, and it means
     * a machine with no OS can still have its storage set up.
     */
    public static final int ACTION_RAID_MODE = 5;

    public static final CustomPacketPayload.Type<FirmwareActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "firmware_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FirmwareActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, FirmwareActionPayload::hostPos,
                    BlockPos.STREAM_CODEC, FirmwareActionPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, FirmwareActionPayload::action,
                    ByteBufCodecs.VAR_LONG, FirmwareActionPayload::ref,
                    ByteBufCodecs.VAR_INT, FirmwareActionPayload::target,
                    FirmwareActionPayload::new);

    @Override
    public CustomPacketPayload.Type<FirmwareActionPayload> type() {
        return TYPE;
    }
}
