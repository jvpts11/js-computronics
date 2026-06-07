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

import java.util.List;

/**
 * Client to server: a Monitor terminal asked to SELECT {@code quantity} of {@code item} from the chosen source Servers into the computer's local storage.
 */
public record TerminalSelectPayload(BlockPos monitorPos, BlockPos hostPos, Item item, long quantity,
                                    List<String> serverKeys) implements CustomPacketPayload {

    public static final int MAX_SERVERS = 64;

    public static final CustomPacketPayload.Type<TerminalSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSelectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalSelectPayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalSelectPayload::hostPos,
                    ByteBufCodecs.registry(Registries.ITEM), TerminalSelectPayload::item,
                    ByteBufCodecs.VAR_LONG, TerminalSelectPayload::quantity,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_SERVERS)),
                    TerminalSelectPayload::serverKeys,
                    TerminalSelectPayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalSelectPayload> type() {
        return TYPE;
    }
}
