/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the Pattern Reader GUI at {@code readerPos} opened (or switched to its ROM tab) and wants the current contents of the adjacent Crafting Computer's Recipe ROM.
 */
public record RequestRomSnapshotPayload(BlockPos readerPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestRomSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_rom_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRomSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestRomSnapshotPayload::readerPos,
                    RequestRomSnapshotPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestRomSnapshotPayload> type() {
        return TYPE;
    }
}
