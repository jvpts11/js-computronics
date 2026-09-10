/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import dev.jstech.core.operation.OperationPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: submit the CRAFT the popup configured, at the level the popup chose. {@code recipe} names
 * which of the recipes that make the result to run, by its index in the list the plan carried; with
 * {@link CraftPlanRequestPayload#ANY} the {@code multiStage} flag decides the old way, pipeline or flat.
 */
public record CraftSubmitPayload(BlockPos monitorPos, BlockPos hostPos,
                                 ItemStack result, long quantity, boolean partial, boolean multiStage,
                                 OperationPriority priority, int recipe)
        implements CustomPacketPayload {

    public CraftSubmitPayload(final BlockPos monitorPos, final BlockPos hostPos, final ItemStack result,
                              final long quantity, final boolean partial, final boolean multiStage,
                              final OperationPriority priority) {
        this(monitorPos, hostPos, result, quantity, partial, multiStage, priority, CraftPlanRequestPayload.ANY);
    }

    public static final CustomPacketPayload.Type<CraftSubmitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "craft_submit"));

    // Built by hand because the record has more components than StreamCodec.composite carries.
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftSubmitPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        BlockPos.STREAM_CODEC.encode(buf, p.monitorPos());
                        BlockPos.STREAM_CODEC.encode(buf, p.hostPos());
                        ItemStack.STREAM_CODEC.encode(buf, p.result());
                        buf.writeVarLong(p.quantity());
                        buf.writeBoolean(p.partial());
                        buf.writeBoolean(p.multiStage());
                        NiGridClickPayload.PRIORITY_CODEC.encode(buf, p.priority());
                        buf.writeVarInt(p.recipe());
                    },
                    buf -> new CraftSubmitPayload(
                            BlockPos.STREAM_CODEC.decode(buf),
                            BlockPos.STREAM_CODEC.decode(buf),
                            ItemStack.STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            NiGridClickPayload.PRIORITY_CODEC.decode(buf),
                            buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<CraftSubmitPayload> type() {
        return TYPE;
    }
}
