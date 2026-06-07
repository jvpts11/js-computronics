/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry in a network's Operations log, with provenance: what was moved, how much was requested vs actually moved, the final status, and the per-source moves (from which Server, how much, to where) so the terminal can show exactly where the items came from and went.
 */
public record OperationRecord(byte type, ItemStack icon, long requested, long moved, byte status,
                              List<MoveRow> moves) {

    public static final byte TYPE_SELECT = 0;
    public static final byte TYPE_INSERT = 1;

    public static final byte STATUS_COMPLETED = 0;
    public static final byte STATUS_PARTIAL = 1;
    public static final byte STATUS_FAILED = 2;

    public static final int MAX_MOVES = 32;

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

    private static final StreamCodec<RegistryFriendlyByteBuf, List<MoveRow>> MOVES_CODEC =
            MoveRow.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MOVES));

    // Built by hand because the record has more components than StreamCodec.composite carries.
    public static final StreamCodec<RegistryFriendlyByteBuf, OperationRecord> STREAM_CODEC =
            StreamCodec.of(
                    (buf, rec) -> {
                        buf.writeByte(rec.type());
                        ItemStack.STREAM_CODEC.encode(buf, rec.icon());
                        buf.writeVarLong(rec.requested());
                        buf.writeVarLong(rec.moved());
                        buf.writeByte(rec.status());
                        MOVES_CODEC.encode(buf, rec.moves());
                    },
                    buf -> new OperationRecord(
                            buf.readByte(),
                            ItemStack.STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readByte(),
                            MOVES_CODEC.decode(buf)));

    public CompoundTag toNbt(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putByte("type", type);
        tag.put("icon", icon.save(registries));
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
        final ItemStack icon = ItemStack.parseOptional(registries, tag.getCompound("icon"));
        final List<MoveRow> moves = new ArrayList<>();
        final ListTag moveList = tag.getList("moves", Tag.TAG_COMPOUND);
        for (int i = 0; i < moveList.size(); i++) {
            final CompoundTag m = moveList.getCompound(i);
            moves.add(new MoveRow(m.getString("from"), m.getLong("qty"), m.getString("to")));
        }
        return new OperationRecord(tag.getByte("type"), icon, tag.getLong("requested"),
                tag.getLong("moved"), tag.getByte("status"), List.copyOf(moves));
    }
}
