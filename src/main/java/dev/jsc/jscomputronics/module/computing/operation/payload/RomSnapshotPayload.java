/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the patterns held in the Recipe ROM of the Crafting Computer adjacent to the open Pattern Reader, so the reader's ROM tab can list them and offer an export to a rewritable disc. Sent on request when the GUI opens and after any action that changes the ROM.
 */
public record RomSnapshotPayload(List<CraftingPattern> patterns) implements CustomPacketPayload {

    // Comfortably above the Recipe ROM hard cap of 50, so a full ROM always encodes.
    public static final int MAX = 64;

    public static final CustomPacketPayload.Type<RomSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "rom_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RomSnapshotPayload> STREAM_CODEC =
            CraftingPattern.STREAM_CODEC.apply(ByteBufCodecs.list(MAX))
                    .map(RomSnapshotPayload::new, RomSnapshotPayload::patterns);

    @Override
    public CustomPacketPayload.Type<RomSnapshotPayload> type() {
        return TYPE;
    }
}
