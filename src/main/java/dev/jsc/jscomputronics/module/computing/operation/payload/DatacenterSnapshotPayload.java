/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: a Datacenter Station's view of its bound section — the aggregate header, the unified item index, and a per-Server breakdown — sent when the Station GUI opens and after each action.
 */
public record DatacenterSnapshotPayload(
        String sectionLabel,
        int serverCount,
        long storageUsed,
        long storageTotal,
        int loadBalanceMode,
        int availableSectionCount,
        long cpuCapacity,
        long ramBuffer,
        int activeOps,
        List<NetworkItemEntry> items,
        List<ServerLine> servers,
        List<DestEntry> destinations
) implements CustomPacketPayload {

    public static final int MAX_ITEMS = 256;
    public static final int MAX_SERVERS = 64;
    public static final int MAX_DESTS = 64;

    /**
     * One Server's storage line in the breakdown.
     */
    public record ServerLine(String name, long used, long total) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerLine> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(64), ServerLine::name,
                        ByteBufCodecs.VAR_LONG, ServerLine::used,
                        ByteBufCodecs.VAR_LONG, ServerLine::total,
                        ServerLine::new);
    }

    /**
     * A computer the section's items can be MOVED to: its position and a display name.
     */
    public record DestEntry(long pos, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, DestEntry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, DestEntry::pos,
                        ByteBufCodecs.stringUtf8(64), DestEntry::name,
                        DestEntry::new);
    }

    public static final CustomPacketPayload.Type<DatacenterSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "datacenter_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DatacenterSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(DatacenterSnapshotPayload::write, DatacenterSnapshotPayload::read);

    private void write(final RegistryFriendlyByteBuf buf) {
        buf.writeUtf(sectionLabel);
        buf.writeVarInt(serverCount);
        buf.writeVarLong(storageUsed);
        buf.writeVarLong(storageTotal);
        buf.writeVarInt(loadBalanceMode);
        buf.writeVarInt(availableSectionCount);
        buf.writeVarLong(cpuCapacity);
        buf.writeVarLong(ramBuffer);
        buf.writeVarInt(activeOps);
        final int itemN = Math.min(items.size(), MAX_ITEMS);
        buf.writeVarInt(itemN);
        for (int i = 0; i < itemN; i++) {
            NetworkItemEntry.STREAM_CODEC.encode(buf, items.get(i));
        }
        final int serverN = Math.min(servers.size(), MAX_SERVERS);
        buf.writeVarInt(serverN);
        for (int i = 0; i < serverN; i++) {
            ServerLine.STREAM_CODEC.encode(buf, servers.get(i));
        }
        final int destN = Math.min(destinations.size(), MAX_DESTS);
        buf.writeVarInt(destN);
        for (int i = 0; i < destN; i++) {
            DestEntry.STREAM_CODEC.encode(buf, destinations.get(i));
        }
    }

    private static DatacenterSnapshotPayload read(final RegistryFriendlyByteBuf buf) {
        final String label = buf.readUtf();
        final int serverCount = buf.readVarInt();
        final long used = buf.readVarLong();
        final long total = buf.readVarLong();
        final int mode = buf.readVarInt();
        final int avail = buf.readVarInt();
        final long cpu = buf.readVarLong();
        final long ram = buf.readVarLong();
        final int ops = buf.readVarInt();
        final int itemN = Math.min(buf.readVarInt(), MAX_ITEMS);
        final List<NetworkItemEntry> items = new ArrayList<>(itemN);
        for (int i = 0; i < itemN; i++) {
            items.add(NetworkItemEntry.STREAM_CODEC.decode(buf));
        }
        final int serverN = Math.min(buf.readVarInt(), MAX_SERVERS);
        final List<ServerLine> servers = new ArrayList<>(serverN);
        for (int i = 0; i < serverN; i++) {
            servers.add(ServerLine.STREAM_CODEC.decode(buf));
        }
        final int destN = Math.min(buf.readVarInt(), MAX_DESTS);
        final List<DestEntry> dests = new ArrayList<>(destN);
        for (int i = 0; i < destN; i++) {
            dests.add(DestEntry.STREAM_CODEC.decode(buf));
        }
        return new DatacenterSnapshotPayload(label, serverCount, used, total, mode, avail,
                cpu, ram, ops, items, servers, dests);
    }

    @Override
    public CustomPacketPayload.Type<DatacenterSnapshotPayload> type() {
        return TYPE;
    }
}
