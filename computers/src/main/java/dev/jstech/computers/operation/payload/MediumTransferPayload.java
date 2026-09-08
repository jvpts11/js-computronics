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
 * Client to server: the player dragged a {@code .dat} file onto a removable medium in the Files
 * explorer. A {@code .dat} is a read-only projection of an item stored in the computer's disks, so
 * this is the one sanctioned way to move that item by hand, not a byte copy of a file, but an
 * atomic item transfer.
 *
 * <p>The server resolves {@code datPath} back to its {@link dev.jstech.computers
 * .storage.StorageKey} by re-projecting the computer's system-disk storage (the projection is
 * deterministic), extracts the stored quantity from the computer's local storage, and inserts it
 * into the medium addressed by {@code mediaVolumeKey} (e.g. {@code media:<readerPos>}). The move is
 * conservative: whatever does not fit on the medium stays in the computer's storage, so no item is
 * ever lost or duplicated.
 *
 * @param hostPos        the computer whose system disk carries the {@code .dat} projection
 * @param monitorPos     the monitor the player is acting through (anti-spoof reach check)
 * @param datPath        the full {@code .dat} path within the system disk's filesystem
 * @param mediaVolumeKey the destination medium volume key ({@code media:<readerPos>})
 */
public record MediumTransferPayload(BlockPos hostPos, BlockPos monitorPos, String datPath,
                                    String mediaVolumeKey) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MediumTransferPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "medium_transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MediumTransferPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, MediumTransferPayload::hostPos,
                    BlockPos.STREAM_CODEC, MediumTransferPayload::monitorPos,
                    ByteBufCodecs.stringUtf8(160), MediumTransferPayload::datPath,
                    ByteBufCodecs.stringUtf8(64), MediumTransferPayload::mediaVolumeKey,
                    MediumTransferPayload::new);

    @Override
    public CustomPacketPayload.Type<MediumTransferPayload> type() {
        return TYPE;
    }
}
