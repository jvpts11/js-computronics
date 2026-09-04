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
 * Client to server: the machine's own power menu — shut down, restart, or just leave the screen.
 * Encerrar from inside the system has to reach the machine, not only the window: a computer whose
 * screen closed is still running, still on the network, and still holding open whatever was open.
 */
public record MachinePowerPayload(BlockPos hostPos, BlockPos monitorPos, int action)
        implements CustomPacketPayload {

    /** Power the machine off: it leaves the network and its session ends. */
    public static final int ACTION_SHUTDOWN = 0;
    /** Power-cycle: off, on, and the screen comes back at the power-on self-test. */
    public static final int ACTION_RESTART = 1;
    /** Leave the screen but keep the machine running. */
    public static final int ACTION_LOG_OFF = 2;

    public static final CustomPacketPayload.Type<MachinePowerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "machine_power"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachinePowerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, MachinePowerPayload::hostPos,
                    BlockPos.STREAM_CODEC, MachinePowerPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, MachinePowerPayload::action,
                    MachinePowerPayload::new);

    @Override
    public CustomPacketPayload.Type<MachinePowerPayload> type() {
        return TYPE;
    }
}
