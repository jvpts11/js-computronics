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
 * Client to server: edits one face of a Crafting Switch — its player-set name and/or its active toggle. The
 * server applies it to the switch's block entity (which then re-syncs to the client via its update tag), after
 * checking the player is in reach of the switch.
 *
 * @param switchPos the Crafting Switch block position
 * @param face      the {@link net.minecraft.core.Direction#get3DDataValue()} of the edited face
 * @param name      the new name for that face's machine
 * @param active    whether that face accepts crafts
 */
public record SetCraftingSwitchFacePayload(BlockPos switchPos, int face, String name, boolean active,
                                           String category)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetCraftingSwitchFacePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "set_crafting_switch_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCraftingSwitchFacePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetCraftingSwitchFacePayload::switchPos,
                    ByteBufCodecs.VAR_INT, SetCraftingSwitchFacePayload::face,
                    ByteBufCodecs.stringUtf8(64), SetCraftingSwitchFacePayload::name,
                    ByteBufCodecs.BOOL, SetCraftingSwitchFacePayload::active,
                    ByteBufCodecs.stringUtf8(32), SetCraftingSwitchFacePayload::category,
                    SetCraftingSwitchFacePayload::new);

    @Override
    public CustomPacketPayload.Type<SetCraftingSwitchFacePayload> type() {
        return TYPE;
    }
}
