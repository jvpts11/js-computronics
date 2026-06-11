/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.common.hardware.PhiCoprocessorSpec;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerConsoleBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Menu for the Supercomputer Console: a read-only survey of the cluster the console touches.
 */
public class SupercomputerConsoleMenu extends AbstractContainerMenu {

    public static final int DATA_FOUND = 0;
    public static final int DATA_ONLINE = 1;
    public static final int DATA_BUDGET = 2;
    public static final int DATA_IN_USE = 3;
    public static final int DATA_UNSLOTTED = 4;
    public static final int DATA_SLOT_BASE = 5;
    public static final int DATA_COUNT = DATA_SLOT_BASE + PhiCoprocessorSpec.SLOT_COUNT;

    private final SupercomputerConsoleBlockEntity console;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public SupercomputerConsoleMenu(final int containerId, final SupercomputerConsoleBlockEntity console) {
        super(ComputingModule.SUPERCOMPUTER_CONSOLE_MENU.get(), containerId);
        this.console = console;
        this.access = ContainerLevelAccess.create(console.getLevel(), console.getBlockPos());
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(data);
    }

    @org.jetbrains.annotations.Nullable
    public static SupercomputerConsoleMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                       final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof SupercomputerConsoleBlockEntity console) {
            return new SupercomputerConsoleMenu(containerId, console);
        }
        return null;
    }

    @Override
    public void broadcastChanges() {
        if (console.getLevel() != null && !console.getLevel().isClientSide()) {
            final HbwInterfaceBlockEntity hub = console.findInterface();
            data.set(DATA_FOUND, hub == null ? 0 : 1);
            data.set(DATA_ONLINE, hub != null && hub.clusterOnline() ? 1 : 0);
            data.set(DATA_BUDGET, hub == null ? 0 : (int) Math.min(Integer.MAX_VALUE, hub.parallelCrafts()));
            data.set(DATA_IN_USE, hub == null ? 0 : hub.craftSlotsInUse());
            data.set(DATA_UNSLOTTED, hub == null ? 0 : hub.unslottedNodes());
            final var slots = hub == null ? java.util.List.<HbwInterfaceBlockEntity.ClusterSlot>of()
                    : hub.clusterSlots();
            for (int i = 0; i < PhiCoprocessorSpec.SLOT_COUNT; i++) {
                data.set(DATA_SLOT_BASE + i, i < slots.size()
                        ? slots.get(i).code() : HbwInterfaceBlockEntity.SLOT_NO_NODE);
            }
        }
        super.broadcastChanges();
    }

    public boolean interfaceFound() {
        return data.get(DATA_FOUND) != 0;
    }

    public boolean clusterOnline() {
        return data.get(DATA_ONLINE) != 0;
    }

    public int budget() {
        return data.get(DATA_BUDGET);
    }

    public int inUse() {
        return data.get(DATA_IN_USE);
    }

    public int unslotted() {
        return data.get(DATA_UNSLOTTED);
    }

    public int slotCode(final int i) {
        return data.get(DATA_SLOT_BASE + i);
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.SUPERCOMPUTER_CONSOLE.get());
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(final Player player, final int index) {
        return net.minecraft.world.item.ItemStack.EMPTY; // read-only console, no item slots
    }
}
