/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.HolderLookup;
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

/**
 * One entry in a network's Operations log, with provenance: what was moved, how much was requested vs actually moved, the final status, and the per-source moves (from which Server, how much, to where) so the terminal can show exactly where the data came from and went.
 */
public record OperationRecord(byte type, StorageKey key, long requested, long moved, byte status,
                              List<MoveRow> moves, List<SubRow> subs) {

    public static final byte TYPE_SELECT = 0;
    public static final byte TYPE_INSERT = 1;
    public static final byte TYPE_DELETE = 2;
    public static final byte TYPE_MOVE = 3;
    public static final byte TYPE_ANALYZE = 4;
    public static final byte TYPE_REINDEX = 5;
    public static final byte TYPE_VACUUM = 6;
    public static final byte TYPE_DROP = 7;

    public static final byte STATUS_COMPLETED = 0;
    public static final byte STATUS_PARTIAL = 1;
    public static final byte STATUS_FAILED = 2;
    public static final byte STATUS_PROCESSING = 3;
    public static final byte STATUS_WAITING = 4;
    public static final byte STATUS_RESOURCE_LOCKED = 5;
    public static final byte STATUS_PENDING = 6;

    public static final int MAX_MOVES = 32;
    public static final int MAX_SUBS = 32;

    public OperationRecord(final byte type, final StorageKey key, final long requested, final long moved,
                           final byte status, final List<MoveRow> moves) {
        this(type, key, requested, moved, status, moves, List.of());
    }

    public OperationRecord withStatus(final byte newStatus) {
        return new OperationRecord(type, key, requested, moved, newStatus, moves, subs);
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
                        buf.writeByte(rec.type());
                        StorageKey.STREAM_CODEC.encode(buf, rec.key());
                        buf.writeVarLong(rec.requested());
                        buf.writeVarLong(rec.moved());
                        buf.writeByte(rec.status());
                        MOVES_CODEC.encode(buf, rec.moves());
                        SUBS_CODEC.encode(buf, rec.subs());
                    },
                    buf -> new OperationRecord(
                            buf.readByte(),
                            StorageKey.STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readByte(),
                            MOVES_CODEC.decode(buf),
                            SUBS_CODEC.decode(buf)));

    public CompoundTag toNbt(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putByte("type", type);
        StorageKey.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), key)
                .result().ifPresent(encoded -> tag.put("icon", encoded));
        tag.putLong("requested", requested);
        tag.putLong("moved", moved);
        tag.putByte("status", status);
        final ListTag moveList = new ListTag();
        for (final MoveRow row : moves) {
            final CompoundTag m = new CompoundTag();
            m.putString("from", row.from());
            m.putLong("qty", row.qty());
            m.putString("to", row.to());
            moveList.add(m);
        }
        tag.put("moves", moveList);
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
        return new OperationRecord(tag.getByte("type"), key, tag.getLong("requested"),
                tag.getLong("moved"), tag.getByte("status"), List.copyOf(moves));
    }
}
