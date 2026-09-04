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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: reopen the system installer at its last beat. The system is already on the
 * disk, but the machine has not restarted since, so it is still the installer that is running —
 * the monitor shows its "reboot" prompt again rather than a system that never booted.
 *
 * @param host         the computer
 * @param monitorPos   the monitor the screen renders on
 * @param firmwareKind the firmware look of the machine's era, as an ordinal
 * @param osName       the system that was installed, for the prompt
 * @param targetLabel  the disk it went onto, for the prompt
 * @param targetSlot   the disk slot to boot when the player restarts ({@code -1} = the default disk)
 * @param failure      empty when the system is on the disk; otherwise why the write was refused, so the
 *                     installer ends on that instead of a "complete" it never earned
 */
public record OpenInstallDonePayload(BlockPos host, BlockPos monitorPos, int firmwareKind, String osName,
                                     String targetLabel, int targetSlot, String failure)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenInstallDonePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "open_install_done"));

    // Seven fields: one past what StreamCodec.composite takes, so the two halves are written by hand.
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenInstallDonePayload> STREAM_CODEC =
            StreamCodec.of(OpenInstallDonePayload::write, OpenInstallDonePayload::read);

    private static void write(final RegistryFriendlyByteBuf buf, final OpenInstallDonePayload payload) {
        buf.writeBlockPos(payload.host);
        buf.writeBlockPos(payload.monitorPos);
        buf.writeVarInt(payload.firmwareKind);
        buf.writeUtf(payload.osName);
        buf.writeUtf(payload.targetLabel);
        buf.writeVarInt(payload.targetSlot);
        buf.writeUtf(payload.failure);
    }

    private static OpenInstallDonePayload read(final RegistryFriendlyByteBuf buf) {
        return new OpenInstallDonePayload(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), buf.readUtf(),
                buf.readUtf(), buf.readVarInt(), buf.readUtf());
    }

    @Override
    public CustomPacketPayload.Type<OpenInstallDonePayload> type() {
        return TYPE;
    }
}
