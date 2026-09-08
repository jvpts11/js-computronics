/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server to client: one item's full detail for Storage Insights: the network total, where it is stored
 * (per server), what recipes it helps make ({@link #usedToMake}, the products of patterns that consume it),
 * and which buses filter it ({@link #buses}). Sent in reply to {@link RequestItemDetailPayload}.
 */
public record ItemDetailPayload(ItemStack item, long total, List<NetworkItemEntry.StorageShare> storedIn,
                                List<ItemStack> usedToMake, List<BusRef> buses) implements CustomPacketPayload {

    public static final int MAX_STORED = 32;
    public static final int MAX_USES = 32;
    public static final int MAX_BUSES = 32;

    /** A bus that filters this item: its name and its kind (Import / Export / Input / Receiving). */
    public record BusRef(String name, String kind) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BusRef> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, BusRef::name,
                        ByteBufCodecs.STRING_UTF8, BusRef::kind,
                        BusRef::new);
    }

    public static final CustomPacketPayload.Type<ItemDetailPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "item_detail"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemDetailPayload> STREAM_CODEC =
            StreamCodec.of(ItemDetailPayload::encode, ItemDetailPayload::decode);

    private static void encode(final RegistryFriendlyByteBuf buf, final ItemDetailPayload p) {
        ItemStack.STREAM_CODEC.encode(buf, p.item);
        buf.writeVarLong(p.total);
        NetworkItemEntry.StorageShare.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STORED)).encode(buf, p.storedIn);
        ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_USES)).encode(buf, p.usedToMake);
        BusRef.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BUSES)).encode(buf, p.buses);
    }

    private static ItemDetailPayload decode(final RegistryFriendlyByteBuf buf) {
        final ItemStack item = ItemStack.STREAM_CODEC.decode(buf);
        final long total = buf.readVarLong();
        final List<NetworkItemEntry.StorageShare> storedIn =
                NetworkItemEntry.StorageShare.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STORED)).decode(buf);
        final List<ItemStack> usedToMake =
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_USES)).decode(buf);
        final List<BusRef> buses = BusRef.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BUSES)).decode(buf);
        return new ItemDetailPayload(item, total, storedIn, usedToMake, buses);
    }

    @Override
    public CustomPacketPayload.Type<ItemDetailPayload> type() {
        return TYPE;
    }
}
