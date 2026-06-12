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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Server Rack: a row of slots holding the Servers, so the player picks which slot a Server goes into rather than dropping it into the first free one.
 */
public class ServerRackMenu extends AbstractContainerMenu {

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
        addPlayerInventory(playerInventory);
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

    private void addPlayerInventory(final Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 148 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 206));
        }
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.SERVER_RACK.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int total = slots.size();

        if (index < RACK_SLOTS) {
            if (!moveItemStackTo(stack, RACK_SLOTS, total, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, RACK_SLOTS, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
