/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Server Rack: a row of slots holding the Servers, so the player picks which slot a Server goes into rather than dropping it into the first free one.
 */
public class ServerRackMenu extends AbstractComputerMenu {

    private static final int RACK_SLOTS = ServerRackBlockEntity.CAPACITY;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public ServerRackMenu(final int containerId, final Inventory playerInventory,
                          final ServerRackBlockEntity be) {
        super(ComputingModule.SERVER_RACK_MENU.get(), containerId);
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.data = be.getDataAccess();

        // One bay row per slot: the Server slot anchors a status line in the manifest.
        final IItemHandler servers = be.getServers();
        for (int i = 0; i < RACK_SLOTS; i++) {
            addSlot(new SlotItemHandler(servers, i, 12, 71 + i * 18));
        }
        addPlayerInventory(playerInventory, 8, 148);
        addDataSlots(data);
    }

    public boolean networkLinked() {
        return data.get(0) != 0;
    }

    public ItemStack serverInBay(final int i) {
        return i >= 0 && i < RACK_SLOTS ? slots.get(i).getItem() : ItemStack.EMPTY;
    }

    @org.jetbrains.annotations.Nullable
    public static ServerRackMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                             final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof ServerRackBlockEntity be) {
            return new ServerRackMenu(containerId, playerInventory, be);
        }
        return null;
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.SERVER_RACK.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMoveBetweenContainerAndPlayer(player, index, RACK_SLOTS);
    }
}
