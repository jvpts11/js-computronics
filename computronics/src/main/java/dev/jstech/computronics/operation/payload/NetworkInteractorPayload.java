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
 */
public record NetworkInteractorPayload(List<NetworkItemEntry> networkItems, List<NetworkItemEntry> localItems,
                                       boolean mainframeOnline, long usedItems, int serverCount,
                                       List<CraftCatalogPayload.Entry> crafts) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 512;

    public static final CustomPacketPayload.Type<NetworkInteractorPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "network_interactor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkInteractorPayload> STREAM_CODEC =
            StreamCodec.composite(
                    NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    NetworkInteractorPayload::networkItems,
                    NetworkItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    NetworkInteractorPayload::localItems,
                    ByteBufCodecs.BOOL, NetworkInteractorPayload::mainframeOnline,
                    ByteBufCodecs.VAR_LONG, NetworkInteractorPayload::usedItems,
                    ByteBufCodecs.VAR_INT, NetworkInteractorPayload::serverCount,
                    CraftCatalogPayload.Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    NetworkInteractorPayload::crafts,
                    NetworkInteractorPayload::new);

    @Override
    public CustomPacketPayload.Type<NetworkInteractorPayload> type() {
        return TYPE;
    }
}
