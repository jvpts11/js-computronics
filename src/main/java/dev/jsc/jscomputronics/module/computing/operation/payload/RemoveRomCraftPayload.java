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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client to server: remove the selected Recipe ROM patterns from a Crafting Computer. Each removal
 * drops the pattern from the ROM and deletes its mirrored {@code .craft} file under {@code crafts/}
 * on the computer's system disk.
 *
 * <p>Indices are zero-based positions in the ROM list; out-of-range entries are skipped. They are
 * applied highest-first by the handler so earlier removals do not shift the later indices.
 *
 * @param hostPos    the position of the Crafting Computer
 * @param romIndices zero-based indices of the ROM patterns to remove
 */
public record RemoveRomCraftPayload(
        BlockPos hostPos,
        List<Integer> romIndices) implements CustomPacketPayload {

    private static final int MAX_INDICES = 50;

    public static final CustomPacketPayload.Type<RemoveRomCraftPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "remove_rom_craft"));

    private record Wire(BlockPos pos, List<Integer> indices) {
        static final StreamCodec<RegistryFriendlyByteBuf, Wire> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, Wire::pos,
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_INDICES)), Wire::indices,
                        Wire::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveRomCraftPayload> STREAM_CODEC =
            Wire.STREAM_CODEC.map(
                    w -> new RemoveRomCraftPayload(w.pos(), w.indices()),
                    p -> new Wire(p.hostPos(), p.romIndices()));

    @Override
    public CustomPacketPayload.Type<RemoveRomCraftPayload> type() {
        return TYPE;
    }
}
