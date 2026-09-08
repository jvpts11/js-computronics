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
 * Client to server: request the Crafting Manager state for a Crafting Computer: the list of
 * {@code .craft} files on the first inserted medium and the patterns currently in the Recipe ROM.
 * The server replies with a {@link CraftManagerStatePayload}.
 *
 * @param hostPos the position of the Crafting Computer
 */
public record RequestCraftManagerPayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestCraftManagerPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "request_craft_manager"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCraftManagerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestCraftManagerPayload::hostPos,
                    RequestCraftManagerPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestCraftManagerPayload> type() {
        return TYPE;
    }
}
