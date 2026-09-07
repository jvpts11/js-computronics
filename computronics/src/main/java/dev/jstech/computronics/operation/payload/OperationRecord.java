/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.operation.OperationPriority;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One entry in a network's Operations log, with provenance: what was moved, how much was requested vs actually moved, the final status, and the per-source moves (from which Server, how much, to where) so the terminal can show exactly where the data came from and went.
 *
 * <p>{@code id} is the Operation's identity while it is in flight, so a view can address it (change its
 * priority, cancel it); an instant Operation that was never queued carries {@link #NO_ID}. {@code priority}
 * is the level it was scheduled at. {@code waitedTicks} counts the ticks it sat queued or waiting before it
 * could run and {@code ranTicks} the ticks it was actually running: together they are how long it took.
 */
public record OperationRecord(UUID id, byte type, StorageKey key, long requested, long moved, byte status,
                              OperationPriority priority, List<MoveRow> moves, List<SubRow> subs,
                              int waitedTicks, int ranTicks) {

    /** The id of a record that never had a live Operation behind it (an instant maintenance record). */
    public static final UUID NO_ID = new UUID(0L, 0L);

    public static final byte TYPE_SELECT = 0;
    public static final byte TYPE_INSERT = 1;
    public static final byte TYPE_DELETE = 2;
    public static final byte TYPE_MOVE = 3;
    public static final byte TYPE_ANALYZE = 4;
    public static final byte TYPE_REINDEX = 5;
    public static final byte TYPE_VACUUM = 6;
    public static final byte TYPE_DROP = 7;
    public static final byte TYPE_CRAFT = 8;

    /*
     * Mirrors the 8-state OperationStatus: COMPLETED, COMPLETED_PARTIAL, FAILED, PROCESSING, WAITING,
     * RESOURCE_LOCKED, PENDING, DISCARDED.
     */
    public static final byte STATUS_COMPLETED = 0;
    public static final byte STATUS_PARTIAL = 1;
    public static final byte STATUS_FAILED = 2;
    public static final byte STATUS_PROCESSING = 3;
    public static final byte STATUS_WAITING = 4;
    public static final byte STATUS_RESOURCE_LOCKED = 5;
    public static final byte STATUS_PENDING = 6;
    public static final byte STATUS_DISCARDED = 7;

    public static final int MAX_MOVES = 32;
    public static final int MAX_SUBS = 32;

    /** An instant Operation's record: no live identity, the default priority, no SubOperation rows, no time. */
    public OperationRecord(final byte type, final StorageKey key, final long requested, final long moved,
                           final byte status, final List<MoveRow> moves) {
        this(NO_ID, type, key, requested, moved, status, OperationPriority.DEFAULT, moves, List.of(), 0, 0);
    }

    /** A live Operation's record before the scheduler stamps its timing. */
    public OperationRecord(final UUID id, final byte type, final StorageKey key, final long requested,
                           final long moved, final byte status, final OperationPriority priority,
                           final List<MoveRow> moves, final List<SubRow> subs) {
        this(id, type, key, requested, moved, status, priority, moves, subs, 0, 0);
    }

    public OperationRecord withStatus(final byte newStatus) {
        return new OperationRecord(id, type, key, requested, moved, newStatus, priority, moves, subs,
                waitedTicks, ranTicks);
    }

    public OperationRecord withPriority(final OperationPriority newPriority) {
        return new OperationRecord(id, type, key, requested, moved, status, newPriority, moves, subs,
                waitedTicks, ranTicks);
    }

    public OperationRecord withTiming(final int waited, final int ran) {
        return new OperationRecord(id, type, key, requested, moved, status, priority, moves, subs, waited, ran);
    }

    /** Whether the Operation delivered everything it was asked for. */
    public boolean completed() {
        return status == STATUS_COMPLETED;
    }

    /** Whether a live Operation stands behind this record (it can be addressed by {@link #id()}). */
    public boolean hasId() {
        return !NO_ID.equals(id);
    }

    public ItemStack icon() {
        return key.stack(1);
    }

    public Component name() {
        return key.displayName();
    }

    public boolean isFluid() {
        return key.isFluid();
    }

    /**
     * One provenance row.
     */
    public record MoveRow(String from, long qty, String to) {

        public static final StreamCodec<RegistryFriendlyByteBuf, MoveRow> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, MoveRow::from,
                        ByteBufCodecs.VAR_LONG, MoveRow::qty,
                        ByteBufCodecs.STRING_UTF8, MoveRow::to,
                        MoveRow::new);
    }

    /**
     * One live SubOperation row: a server's share of the Operation and how far along it is.
     */
    public record SubRow(String server, long planned, long moved, byte state) {

        public static final byte SUB_PENDING = 0;
        public static final byte SUB_READING = 1;
        public static final byte SUB_STREAMING = 2;
        public static final byte SUB_COMPLETED = 3;

        public static final StreamCodec<RegistryFriendlyByteBuf, SubRow> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SubRow::server,
                        ByteBufCodecs.VAR_LONG, SubRow::planned,
                        ByteBufCodecs.VAR_LONG, SubRow::moved,
                        ByteBufCodecs.BYTE, SubRow::state,
                        SubRow::new);
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<MoveRow>> MOVES_CODEC =
            MoveRow.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MOVES));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<SubRow>> SUBS_CODEC =
            SubRow.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SUBS));

    // Built by hand because the record has more components than StreamCodec.composite carries.
    public static final StreamCodec<RegistryFriendlyByteBuf, OperationRecord> STREAM_CODEC =
            StreamCodec.of(
                    (buf, rec) -> {
                        UUIDUtil.STREAM_CODEC.encode(buf, rec.id());
                        buf.writeByte(rec.type());
                        StorageKey.STREAM_CODEC.encode(buf, rec.key());
                        buf.writeVarLong(rec.requested());
                        buf.writeVarLong(rec.moved());
                        buf.writeByte(rec.status());
                        buf.writeByte(rec.priority().ordinal());
                        MOVES_CODEC.encode(buf, rec.moves());
                        SUBS_CODEC.encode(buf, rec.subs());
                        buf.writeVarInt(rec.waitedTicks());
                        buf.writeVarInt(rec.ranTicks());
                    },
                    buf -> new OperationRecord(
                            UUIDUtil.STREAM_CODEC.decode(buf),
                            buf.readByte(),
                            StorageKey.STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readByte(),
                            OperationPriority.byOrdinal(buf.readByte()),
                            MOVES_CODEC.decode(buf),
                            SUBS_CODEC.decode(buf),
                            buf.readVarInt(),
                            buf.readVarInt()));

    public CompoundTag toNbt(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        if (hasId()) {
            tag.putUUID("id", id);
        }
        tag.putByte("type", type);
        StorageKey.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), key)
                .result().ifPresent(encoded -> tag.put("icon", encoded));
        tag.putLong("requested", requested);
        tag.putLong("moved", moved);
        tag.putByte("status", status);
        tag.putByte("priority", (byte) priority.ordinal());
        tag.putInt("waited", waitedTicks);
        tag.putInt("ran", ranTicks);
        final ListTag moveList = new ListTag();
        for (final MoveRow row : moves) {
            final CompoundTag m = new CompoundTag();
            m.putString("from", row.from());
            m.putLong("qty", row.qty());
            m.putString("to", row.to());
            moveList.add(m);
        }
        tag.put("moves", moveList);
        final ListTag subList = new ListTag();
        for (final SubRow row : subs) {
            final CompoundTag s = new CompoundTag();
            s.putString("server", row.server());
            s.putLong("planned", row.planned());
            s.putLong("moved", row.moved());
            s.putByte("state", row.state());
            subList.add(s);
        }
        tag.put("subs", subList);
        return tag;
    }

    public static OperationRecord fromNbt(final CompoundTag tag, final HolderLookup.Provider registries) {
        final StorageKey key = StorageKey.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("icon"))
                .result().orElseGet(() -> StorageKey.of(Items.BARRIER));
        final List<MoveRow> moves = new ArrayList<>();
        final ListTag moveList = tag.getList("moves", Tag.TAG_COMPOUND);
        for (int i = 0; i < moveList.size(); i++) {
            final CompoundTag m = moveList.getCompound(i);
            moves.add(new MoveRow(m.getString("from"), m.getLong("qty"), m.getString("to")));
        }
        final List<SubRow> subs = new ArrayList<>();
        final ListTag subList = tag.getList("subs", Tag.TAG_COMPOUND);
        for (int i = 0; i < subList.size(); i++) {
            final CompoundTag s = subList.getCompound(i);
            subs.add(new SubRow(s.getString("server"), s.getLong("planned"), s.getLong("moved"), s.getByte("state")));
        }
        final UUID id = tag.hasUUID("id") ? tag.getUUID("id") : NO_ID;
        final OperationPriority priority = tag.contains("priority")
                ? OperationPriority.byOrdinal(tag.getByte("priority")) : OperationPriority.DEFAULT;
        return new OperationRecord(id, tag.getByte("type"), key, tag.getLong("requested"),
                tag.getLong("moved"), tag.getByte("status"), priority, List.copyOf(moves), List.copyOf(subs),
                tag.getInt("waited"), tag.getInt("ran"));
    }
}
