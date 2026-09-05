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

/**
 * Client to server: a non-destructive index-maintenance action from the terminal's Maintenance tab (ANALYZE / REINDEX / VACUUM).
 */
public record TerminalMaintenancePayload(BlockPos monitorPos, BlockPos hostPos, int action)
        implements CustomPacketPayload {

    public static final int ACTION_ANALYZE = 0;
    public static final int ACTION_REINDEX = 1;
    public static final int ACTION_VACUUM = 2;

    public static final CustomPacketPayload.Type<TerminalMaintenancePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_maintenance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalMaintenancePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalMaintenancePayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalMaintenancePayload::hostPos,
                    ByteBufCodecs.VAR_INT, TerminalMaintenancePayload::action,
                    TerminalMaintenancePayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalMaintenancePayload> type() {
        return TYPE;
    }
}
