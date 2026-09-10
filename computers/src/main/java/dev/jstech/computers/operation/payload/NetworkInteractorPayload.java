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

import java.util.List;

/**
 * Server to client: the storage snapshot the Network Interactor desktop app renders. It mirrors what
 * the terminal shows: the network item grid, the host's local item grid, and the network status line.
 *
 * @param networkItems    the items held across the whole network (Network Storage tab)
 * @param localItems      the items on this computer's own disks (Local Storage tab)
 * @param mainframeOnline  whether the network has a live orchestrating Mainframe
 * @param usedItems       the network's used storage in item-equivalents
 * @param serverCount     the number of Servers on the network
 * @param crafts          the network's craft catalog (Crafting tab), with per-entry availability dots
 * @param favourites      the data this computer keeps starred, by {@link dev.jstech.computers.storage.StorageKey#id()}
 */
public record NetworkInteractorPayload(List<NetworkItemEntry> networkItems, List<NetworkItemEntry> localItems,
                                       boolean mainframeOnline, long usedItems, int serverCount,
                                       List<CraftCatalogPayload.Entry> crafts, List<String> favourites)
        implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 512;
    public static final int MAX_FAVOURITES = dev.jstech.computers.program.ComputerSettings.MAX_FAVOURITES;
    public static final int MAX_ID = 128;

    public static final CustomPacketPayload.Type<NetworkInteractorPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_interactor"));

    // Seven things to carry, one past what composite takes, so the codec is written out.
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkInteractorPayload> STREAM_CODEC =
            StreamCodec.of(NetworkInteractorPayload::encode, NetworkInteractorPayload::decode);

    private static void encode(final RegistryFriendlyByteBuf buf, final NetworkInteractorPayload p) {
        NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)).encode(buf, p.networkItems);
        NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)).encode(buf, p.localItems);
        buf.writeBoolean(p.mainframeOnline);
        buf.writeVarLong(p.usedItems);
        buf.writeVarInt(p.serverCount);
        CraftCatalogPayload.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)).encode(buf, p.crafts);
        ByteBufCodecs.stringUtf8(MAX_ID).apply(ByteBufCodecs.list(MAX_FAVOURITES)).encode(buf, p.favourites);
    }

    private static NetworkInteractorPayload decode(final RegistryFriendlyByteBuf buf) {
        final List<NetworkItemEntry> network = NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)).decode(buf);
        final List<NetworkItemEntry> local = NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)).decode(buf);
        final boolean online = buf.readBoolean();
        final long used = buf.readVarLong();
        final int servers = buf.readVarInt();
        final List<CraftCatalogPayload.Entry> crafts =
                CraftCatalogPayload.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)).decode(buf);
        final List<String> favourites = ByteBufCodecs.stringUtf8(MAX_ID).apply(ByteBufCodecs.list(MAX_FAVOURITES)).decode(buf);
        return new NetworkInteractorPayload(network, local, online, used, servers, crafts, favourites);
    }

    @Override
    public CustomPacketPayload.Type<NetworkInteractorPayload> type() {
        return TYPE;
    }
}
