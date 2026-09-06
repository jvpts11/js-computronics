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
 * Client to server: the Automation Manager's "New job" form. The server compiles the fields into a saved
 * IQL job (so no IQL is typed by the player): Keep Stock becomes {@code WHEN qty(item) < amount AS CRAFT
 * amount item}, Batch Craft becomes {@code EVERY interval AS CRAFT amount item}, and Periodic Move becomes
 * {@code EVERY interval AS MOVE amount item FROM from TO to}. Fields not used by a type are ignored.
 */
public record CreateAutomationJobPayload(BlockPos host, BlockPos monitorPos, int jobType, String name,
                                         String item, long amount, String from, String to, String interval)
        implements CustomPacketPayload {

    public static final int TYPE_KEEP_STOCK = 0;
    public static final int TYPE_BATCH_CRAFT = 1;
    public static final int TYPE_PERIODIC_MOVE = 2;
    /** Runs a stored .iql script (its name in {@code item}) every {@code interval}. */
    public static final int TYPE_IQL_SCRIPT = 3;

    public static final CustomPacketPayload.Type<CreateAutomationJobPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "create_automation_job"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateAutomationJobPayload> STREAM_CODEC =
            StreamCodec.of(CreateAutomationJobPayload::encode, CreateAutomationJobPayload::decode);

    private static void encode(final RegistryFriendlyByteBuf buf, final CreateAutomationJobPayload p) {
        BlockPos.STREAM_CODEC.encode(buf, p.host);
        BlockPos.STREAM_CODEC.encode(buf, p.monitorPos);
        buf.writeVarInt(p.jobType);
        buf.writeUtf(p.name);
        buf.writeUtf(p.item);
        buf.writeVarLong(p.amount);
        buf.writeUtf(p.from);
        buf.writeUtf(p.to);
        buf.writeUtf(p.interval);
    }

    private static CreateAutomationJobPayload decode(final RegistryFriendlyByteBuf buf) {
        final BlockPos host = BlockPos.STREAM_CODEC.decode(buf);
        final BlockPos monitorPos = BlockPos.STREAM_CODEC.decode(buf);
        final int type = buf.readVarInt();
        final String name = buf.readUtf();
        final String item = buf.readUtf();
        final long amount = buf.readVarLong();
        final String from = buf.readUtf();
        final String to = buf.readUtf();
        final String interval = buf.readUtf();
        return new CreateAutomationJobPayload(host, monitorPos, type, name, item, amount, from, to, interval);
    }

    @Override
    public CustomPacketPayload.Type<CreateAutomationJobPayload> type() {
        return TYPE;
    }
}
