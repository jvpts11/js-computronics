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
 * Client to server: the power-on self-test on this monitor ended. With {@code enterSetup} the player
 * pressed DEL during the POST and wants the firmware setup; otherwise the computer boots whatever its
 * boot target is (the installed OS, or the setup itself when no system is present).
 */
public record PostCompletePayload(BlockPos hostPos, BlockPos monitorPos,
                                  boolean enterSetup) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PostCompletePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "post_complete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PostCompletePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PostCompletePayload::hostPos,
                    BlockPos.STREAM_CODEC, PostCompletePayload::monitorPos,
                    ByteBufCodecs.BOOL, PostCompletePayload::enterSetup,
                    PostCompletePayload::new);

    @Override
    public CustomPacketPayload.Type<PostCompletePayload> type() {
        return TYPE;
    }
}
