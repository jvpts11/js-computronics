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
 * Client to server: the Crafting Manager's Machines tab sets the per-machine concurrency config on a Crafting
 * Computer. The server clamps and applies it, then replies with a fresh {@link CraftManagerStatePayload}.
 *
 * @param hostPos    the Crafting Computer being edited
 * @param machineKey the machine key (its block registry id, the same key a pattern targets)
 * @param maxJobs    how many processing jobs may run on the machine at once (clamped >= 1 server-side)
 * @param locked     true to pause the machine
 * @param feedMax    true to fill the machine instead of feeding one lot at a time
 */
public record SetMachineConfigPayload(BlockPos hostPos, String machineKey, int maxJobs, boolean locked,
                                      boolean feedMax) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetMachineConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "set_machine_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetMachineConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetMachineConfigPayload::hostPos,
                    ByteBufCodecs.stringUtf8(80), SetMachineConfigPayload::machineKey,
                    ByteBufCodecs.VAR_INT, SetMachineConfigPayload::maxJobs,
                    ByteBufCodecs.BOOL, SetMachineConfigPayload::locked,
                    ByteBufCodecs.BOOL, SetMachineConfigPayload::feedMax,
                    SetMachineConfigPayload::new);

    @Override
    public CustomPacketPayload.Type<SetMachineConfigPayload> type() {
        return TYPE;
    }
}
