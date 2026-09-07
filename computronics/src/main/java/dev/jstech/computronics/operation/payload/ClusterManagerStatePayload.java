/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: everything the Cluster Manager shows. The machine's own state (card, reach,
 * lanes, discs), every cluster the network reaches, the selected cluster in detail (its nodes and,
 * for a supercomputer, its craft queue), the install job in flight, and, for a datacenter, the
 * section's inventory and the computers a move-out can go to.
 */
public record ClusterManagerStatePayload(Head head, List<WireCluster> clusters, Detail detail, WireJob job,
                                         List<NetworkItemEntry> items, List<WireDest> dests)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClusterManagerStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "cluster_manager_state"));

    public static final int MAX_CLUSTERS = 64;
    public static final int MAX_NODES = 64;
    public static final int MAX_QUEUE = 32;
    public static final int MAX_LANES = 16;
    public static final int MAX_ITEMS = 256;
    public static final int MAX_DESTS = 64;

    public static final int KIND_SUPERCOMPUTER = 0;
    public static final int KIND_DATACENTER = 1;
    public static final int KIND_AI = 2;

    /** The machine: whether it can manage at all, how far, how wide, and what discs are in. */
    public record Head(boolean hasCard, int reach, int lanes, String mediumSystem, String mediumProgram, String status) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Head> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Head::hasCard,
                ByteBufCodecs.VAR_INT, Head::reach,
                ByteBufCodecs.VAR_INT, Head::lanes,
                ByteBufCodecs.stringUtf8(64), Head::mediumSystem,
                ByteBufCodecs.stringUtf8(64), Head::mediumProgram,
                ByteBufCodecs.stringUtf8(160), Head::status,
                Head::new);
    }

    /**
     * One cluster in the list. For a supercomputer {@code a}/{@code b} are crafts in use / budget; for a
     * datacenter they are storage used / total; {@code sub} is the one-line detail under the name.
     */
    public record WireCluster(int kind, int index, String name, boolean online, int nodes, long a, long b,
                              int balance, String sub, boolean reachable) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WireCluster> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeVarInt(c.kind()).writeVarInt(c.index()).writeUtf(c.name(), 64).writeBoolean(c.online())
                            .writeVarInt(c.nodes()).writeVarLong(c.a()).writeVarLong(c.b()).writeVarInt(c.balance())
                            .writeUtf(c.sub(), 96).writeBoolean(c.reachable());
                },
                buf -> new WireCluster(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readBoolean(),
                        buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readUtf(96),
                        buf.readBoolean()));
    }

    /** One rack row of the selected cluster. */
    /*
     * What one machine in a cluster is doing right now, in the order the manager checks it: a machine that
     * is not assembled cannot be switched on, one whose bay is off cannot be installed to, and so on.
     */
    /** Seated, but the hardware does not make a working computer. */
    public static final int STATE_INCOMPLETE = 0;
    /** Assembled, but its bay switch is off, so the machine has no power. */
    public static final int STATE_BAY_OFF = 1;
    /** A job is writing this machine at this moment. */
    public static final int STATE_INSTALLING = 2;
    /** A supercomputer node with no coprocessor: it holds a cluster slot but contributes nothing. */
    public static final int STATE_NO_COPROCESSOR = 3;
    /** A supercomputer node whose coprocessor is too weak for the slot it landed in. */
    public static final int STATE_UNDER_RATED = 4;
    /** A supercomputer node past the rated slots: seated and powered, but inert. */
    public static final int STATE_UNSLOTTED = 5;
    /** Running, with no system on its disk yet. */
    public static final int STATE_NO_SYSTEM = 6;
    /** Running its system, doing its job. */
    public static final int STATE_ONLINE = 7;

    public record WireNode(long rackPos, int rackIndex, int row, String name, String osLabel, String programs,
                           int phiModel, boolean bayOn, int slotIndex, int code, long used, long total, int state) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WireNode> STREAM_CODEC = StreamCodec.of(
                (buf, n) -> {
                    buf.writeVarLong(n.rackPos()).writeVarInt(n.rackIndex()).writeVarInt(n.row()).writeUtf(n.name(), 64)
                            .writeUtf(n.osLabel(), 64).writeUtf(n.programs(), 160).writeVarInt(n.phiModel())
                            .writeBoolean(n.bayOn()).writeVarInt(n.slotIndex()).writeVarInt(n.code())
                            .writeVarLong(n.used()).writeVarLong(n.total()).writeVarInt(n.state());
                },
                buf -> new WireNode(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64),
                        buf.readUtf(64), buf.readUtf(160), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt()));
    }

    /** One craft in a supercomputer's queue. */
    public record WireCraft(String label, String requester, int slots, boolean waiting) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WireCraft> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(96), WireCraft::label,
                ByteBufCodecs.stringUtf8(48), WireCraft::requester,
                ByteBufCodecs.VAR_INT, WireCraft::slots,
                ByteBufCodecs.BOOL, WireCraft::waiting,
                WireCraft::new);
    }

    /** The selected cluster: which one, its header line, and what it holds. */
    public record Detail(int kind, int index, String name, String sub, boolean online, int balance,
                         List<WireNode> nodes, List<WireCraft> queue) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Detail> STREAM_CODEC = StreamCodec.of(
                (buf, d) -> {
                    buf.writeVarInt(d.kind()).writeVarInt(d.index()).writeUtf(d.name(), 64).writeUtf(d.sub(), 128)
                            .writeBoolean(d.online()).writeVarInt(d.balance());
                    buf.writeVarInt(d.nodes().size());
                    for (final WireNode n : d.nodes()) {
                        WireNode.STREAM_CODEC.encode(buf, n);
                    }
                    buf.writeVarInt(d.queue().size());
                    for (final WireCraft c : d.queue()) {
                        WireCraft.STREAM_CODEC.encode(buf, c);
                    }
                },
                buf -> {
                    final int kind = buf.readVarInt();
                    final int index = buf.readVarInt();
                    final String name = buf.readUtf(64);
                    final String sub = buf.readUtf(128);
                    final boolean online = buf.readBoolean();
                    final int balance = buf.readVarInt();
                    final int n = Math.min(MAX_NODES, buf.readVarInt());
                    final List<WireNode> nodes = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        nodes.add(WireNode.STREAM_CODEC.decode(buf));
                    }
                    final int q = Math.min(MAX_QUEUE, buf.readVarInt());
                    final List<WireCraft> queue = new ArrayList<>(q);
                    for (int i = 0; i < q; i++) {
                        queue.add(WireCraft.STREAM_CODEC.decode(buf));
                    }
                    return new Detail(kind, index, name, sub, online, balance, nodes, queue);
                });

        public static Detail none() {
            return new Detail(-1, -1, "", "", false, 0, List.of(), List.of());
        }
    }

    /** One node being written by the job. */
    public record WireLane(String name, int permille) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WireLane> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), WireLane::name,
                ByteBufCodecs.VAR_INT, WireLane::permille,
                WireLane::new);
    }

    /** The bulk install in flight, or {@link #none()}. */
    public record WireJob(boolean active, int kind, String label, int clusterKind, int clusterIndex, int done,
                          int skipped, int queued, int total, int elapsedTicks, boolean cancelled, List<WireLane> lanes,
                          String lastSummary) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WireJob> STREAM_CODEC = StreamCodec.of(
                (buf, j) -> {
                    buf.writeBoolean(j.active()).writeVarInt(j.kind()).writeUtf(j.label(), 64).writeVarInt(j.clusterKind())
                            .writeVarInt(j.clusterIndex()).writeVarInt(j.done()).writeVarInt(j.skipped()).writeVarInt(j.queued())
                            .writeVarInt(j.total()).writeVarInt(j.elapsedTicks()).writeBoolean(j.cancelled());
                    buf.writeVarInt(j.lanes().size());
                    for (final WireLane lane : j.lanes()) {
                        WireLane.STREAM_CODEC.encode(buf, lane);
                    }
                    buf.writeUtf(j.lastSummary(), 160);
                },
                buf -> {
                    final boolean active = buf.readBoolean();
                    final int kind = buf.readVarInt();
                    final String label = buf.readUtf(64);
                    final int clusterKind = buf.readVarInt();
                    final int clusterIndex = buf.readVarInt();
                    final int done = buf.readVarInt();
                    final int skipped = buf.readVarInt();
                    final int queued = buf.readVarInt();
                    final int total = buf.readVarInt();
                    final int elapsed = buf.readVarInt();
                    final boolean cancelled = buf.readBoolean();
                    final int n = Math.min(MAX_LANES, buf.readVarInt());
                    final List<WireLane> lanes = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        lanes.add(WireLane.STREAM_CODEC.decode(buf));
                    }
                    final String last = buf.readUtf(160);
                    return new WireJob(active, kind, label, clusterKind, clusterIndex, done, skipped, queued, total,
                            elapsed, cancelled, lanes, last);
                });

        public static WireJob none(final String lastSummary) {
            return new WireJob(false, 0, "", -1, -1, 0, 0, 0, 0, 0, false, List.of(), lastSummary);
        }
    }

    /** A computer a move-out can go to. */
    public record WireDest(long pos, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WireDest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, WireDest::pos,
                ByteBufCodecs.stringUtf8(64), WireDest::name,
                WireDest::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ClusterManagerStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    Head.STREAM_CODEC, ClusterManagerStatePayload::head,
                    WireCluster.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CLUSTERS)), ClusterManagerStatePayload::clusters,
                    Detail.STREAM_CODEC, ClusterManagerStatePayload::detail,
                    WireJob.STREAM_CODEC, ClusterManagerStatePayload::job,
                    NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), ClusterManagerStatePayload::items,
                    WireDest.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DESTS)), ClusterManagerStatePayload::dests,
                    ClusterManagerStatePayload::new);

    @Override
    public CustomPacketPayload.Type<ClusterManagerStatePayload> type() {
        return TYPE;
    }
}
