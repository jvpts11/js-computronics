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
 * Client to server: the name typed in a Server Router's config GUI.
 */
public record RenameServerRouterPayload(BlockPos routerPos, String name) implements CustomPacketPayload {

    public static final int MAX_LEN = 32;

    public static final CustomPacketPayload.Type<RenameServerRouterPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "rename_server_router"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameServerRouterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RenameServerRouterPayload::routerPos,
                    ByteBufCodecs.stringUtf8(MAX_LEN), RenameServerRouterPayload::name,
                    RenameServerRouterPayload::new);

    @Override
    public CustomPacketPayload.Type<RenameServerRouterPayload> type() {
        return TYPE;
    }
}
