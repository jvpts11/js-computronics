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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: requests that the server scan the 6 neighbours of {@code computerPos} for an
 * adjacent {@link dev.jstech.computers.os.media.MediaReaderBlockEntity} holding
 * an OS installer medium, validate the era gate, and call
 * {@link dev.jstech.computers.blockentity.AbstractComputerBlockEntity#installOs}
 * when all conditions are met.
 *
 * <p>A single computer position is sufficient because the install logic runs on the server and
 * discovers the reader itself via neighbour scanning; no face or reader position is sent.
 */
public record InstallOsPayload(BlockPos computerPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InstallOsPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "install_os"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InstallOsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, InstallOsPayload::computerPos,
                    InstallOsPayload::new);

    @Override
    public CustomPacketPayload.Type<InstallOsPayload> type() {
        return TYPE;
    }
}
