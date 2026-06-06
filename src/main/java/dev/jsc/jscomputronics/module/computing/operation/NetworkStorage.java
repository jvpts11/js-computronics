/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.network.ServerNode;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A flat view of every Server's storage on one network, gathered from the Rack block entities.
 */
public final class NetworkStorage {

    private final List<IItemHandler> servers;

    private NetworkStorage(final List<IItemHandler> servers) {
        this.servers = servers;
    }

    public static NetworkStorage of(final ServerLevel level, final NetworkUuid network) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<IItemHandler> handlers = new ArrayList<>();
        for (final ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    handlers.add(rack.getServerStorage(loc.slot()));
                }
            });
        }
        return new NetworkStorage(handlers);
    }

    public Map<Item, Long> query() {
        final Map<Item, Long> totals = new HashMap<>();
        for (final IItemHandler handler : servers) {
            for (int i = 0; i < handler.getSlots(); i++) {
                final ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    totals.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                }
            }
        }
        return totals;
    }

    public long count(final Item item) {
        long total = 0L;
        for (final IItemHandler handler : servers) {
            for (int i = 0; i < handler.getSlots(); i++) {
                final ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && stack.is(item)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    public long select(final Item item, final long amount, final IItemHandler destination) {
        long moved = 0L;
        for (final IItemHandler handler : servers) {
            for (int i = 0; i < handler.getSlots() && moved < amount; i++) {
                final ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty() || !stack.is(item)) {
                    continue;
                }
                final int want = (int) Math.min(amount - moved, stack.getCount());
                final ItemStack offered = handler.extractItem(i, want, true);
                if (offered.isEmpty()) {
                    continue;
                }
                final ItemStack leftover = ItemHandlerHelper.insertItem(destination, offered, false);
                final int accepted = offered.getCount() - leftover.getCount();
                if (accepted > 0) {
                    handler.extractItem(i, accepted, false);
                    moved += accepted;
                }
                if (!leftover.isEmpty()) {
                    return moved; // destination full
                }
            }
        }
        return moved;
    }

    public int insert(final ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (final IItemHandler handler : servers) {
            remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
            if (remaining.isEmpty()) {
                break;
            }
        }
        return stack.getCount() - remaining.getCount();
    }

    public long delete(final Item item, final long amount) {
        long destroyed = 0L;
        for (final IItemHandler handler : servers) {
            for (int i = 0; i < handler.getSlots() && destroyed < amount; i++) {
                final ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty() || !stack.is(item)) {
                    continue;
                }
                final int want = (int) Math.min(amount - destroyed, stack.getCount());
                destroyed += handler.extractItem(i, want, false).getCount();
            }
        }
        return destroyed;
    }

    public boolean isEmpty() {
        return servers.isEmpty();
    }
}
