/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Client to server: the player asked a Personal Computer to SELECT {@code quantity} of {@code item} from the network into its local storage.
 */
public record NetworkSelectPayload(BlockPos pcPos, Item item, long quantity) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NetworkSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSelectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, NetworkSelectPayload::pcPos,
                    ByteBufCodecs.registry(Registries.ITEM), NetworkSelectPayload::item,
                    ByteBufCodecs.VAR_LONG, NetworkSelectPayload::quantity,
                    NetworkSelectPayload::new);

    @Override
    public CustomPacketPayload.Type<NetworkSelectPayload> type() {
        return TYPE;
    }
}
