/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.common.network.ServerNode;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Server Rack: a passive container (network Category A, no UUID of its own) that houses up to {@link #CAPACITY} Server items.
 */
public class ServerRackBlockEntity extends BlockEntity {

    public static final int CAPACITY = 8;

    private final ItemStackHandler servers = new ItemStackHandler(CAPACITY) {
        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return stack.getItem() instanceof ServerItem;
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 1; // each Server is unique
        }

        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
        }
    };

    private final Map<UUID, NetworkUuid> registered = new HashMap<>();

    private final net.minecraft.world.inventory.ContainerData data =
            new net.minecraft.world.inventory.SimpleContainerData(1);

    public ServerRackBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.SERVER_RACK_BE.get(), pos, state);
    }

    public ItemStackHandler getServers() {
        return servers;
    }

    public net.minecraft.world.inventory.ContainerData getDataAccess() {
        return data;
    }

    public dev.jsc.jscomputronics.module.computing.storage.ServerStore getServerStorage(final int slot) {
        return new dev.jsc.jscomputronics.module.computing.storage.ServerStore(this, slot);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final ServerRackBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final NetworkUuid network = adjacentNetwork(level, system);
        data.set(0, network != null ? 1 : 0);

        final Set<UUID> present = new HashSet<>();
        for (int i = 0; i < CAPACITY; i++) {
            final ItemStack stack = servers.getStackInSlot(i);
            if (!(stack.getItem() instanceof ServerItem)) {
                continue;
            }
            // Only an assembled Server (board + PSU) is a real node; an empty
            // Server sitting in the Rack is inert storage and not registered.
            if (ServerItem.build(stack) == null) {
                continue;
            }
            final UUID node = ensureNodeUuid(stack);
            present.add(node);

            final NetworkUuid previous = registered.get(node);
            if (network == null) {
                // No cable / not networked: the Server is inert, drop its registration.
                if (previous != null) {
                    system.unregisterServer(previous, new NodeUuid(node));
                    registered.remove(node);
                }
                continue;
            }
            if (previous != null && !previous.equals(network)) {
                system.unregisterServer(previous, new NodeUuid(node));
            }
            system.registerServer(new ServerNode(new NodeUuid(node), network, ServerItem.storageMb(stack)),
                    worldPosition.asLong(), i);
            registered.put(node, network);
        }

        // Unregister Servers that have left the Rack since the last tick.
        registered.entrySet().removeIf(entry -> {
            if (!present.contains(entry.getKey())) {
                system.unregisterServer(entry.getValue(), new NodeUuid(entry.getKey()));
                return true;
            }
            return false;
        });
    }

    private UUID ensureNodeUuid(final ItemStack stack) {
        UUID node = ServerItem.nodeUuid(stack);
        if (node == null) {
            node = UUID.randomUUID();
            stack.set(ComputingModule.SERVER_NODE_UUID.get(), node);
            setChanged();
        }
        return node;
    }

    private NetworkUuid adjacentNetwork(final ServerLevel level, final NetworkSystem system) {
        for (final Direction direction : Direction.values()) {
            final BlockPos neighbor = worldPosition.relative(direction);
            if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock) {
                final var net = system.connectivity().networkOf(neighbor.asLong());
                if (net.isPresent()) {
                    return net.get();
                }
            }
        }
        return null;
    }

    public void onBroken(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        registered.forEach((node, network) -> system.unregisterServer(network, new NodeUuid(node)));
        registered.clear();
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        servers.deserializeNBT(registries, tag.getCompound("Servers"));
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Servers", servers.serializeNBT(registries));
    }
}
