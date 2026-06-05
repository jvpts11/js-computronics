/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import net.minecraft.core.BlockPos;
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
 * Menu for the Mainframe: the 18 hardware slots (motherboard, CPUs, RAM, GPUs, PSU — each restricted to its component category by the block entity's item handler) plus the player inventory, with powered/capacity/queues/buffer synced for the screen.
 */
public class MainframeMenu extends AbstractContainerMenu {

    private static final int HARDWARE_SLOTS = MainframeBlockEntity.TOTAL_SLOTS;

    private final MainframeBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public MainframeMenu(final int containerId, final Inventory playerInventory,
                         final MainframeBlockEntity be) {
        super(ComputingModule.MAINFRAME_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        final IItemHandler hardware = be.getInventory();
        addSlot(new SlotItemHandler(hardware, MainframeBlockEntity.MOTHERBOARD_SLOT, 8, 28));
        addSlot(new SlotItemHandler(hardware, MainframeBlockEntity.PSU_SLOT, 8, 72));
        for (int i = 0; i < MainframeBlockEntity.CPU_SLOTS; i++) {
            addSlot(new SlotItemHandler(hardware, MainframeBlockEntity.CPU_SLOTS_START + i, 44 + i * 18, 28));
        }
        for (int i = 0; i < MainframeBlockEntity.RAM_SLOTS; i++) {
            addSlot(new SlotItemHandler(hardware, MainframeBlockEntity.RAM_SLOTS_START + i,
                    44 + (i % 4) * 18, 60 + (i / 4) * 18));
        }
        for (int i = 0; i < MainframeBlockEntity.GPU_SLOTS; i++) {
            addSlot(new SlotItemHandler(hardware, MainframeBlockEntity.GPU_SLOTS_START + i, 44 + i * 18, 110));
        }

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    public MainframeMenu(final int containerId, final Inventory playerInventory,
                         final RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static MainframeBlockEntity resolve(final Inventory inv, final BlockPos pos) {
        return (MainframeBlockEntity) inv.player.level().getBlockEntity(pos);
    }

    private void addPlayerInventory(final Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 138 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 196));
        }
    }

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_AUTOSTART = 1;

    public boolean isRunning() {
        return data.get(0) != 0;
    }

    public boolean buildValid() {
        return data.get(1) != 0;
    }

    public long capacity() {
        return data.get(2);
    }

    public int parallelQueues() {
        return data.get(3);
    }

    public long ramBuffer() {
        return data.get(4);
    }

    public boolean isAutoStart() {
        return data.get(5) != 0;
    }

    public boolean isManualOn() {
        return data.get(6) != 0;
    }

    public int networkState() {
        return data.get(7);
    }

    public int pendingOps() {
        return data.get(8);
    }

    public int runningOps() {
        return data.get(9);
    }

    public int completedOps() {
        return data.get(10);
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (id == BUTTON_POWER) {
            blockEntity.togglePower();
            return true;
        }
        if (id == BUTTON_AUTOSTART) {
            blockEntity.toggleAutoStart();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.MAINFRAME.get());
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

        if (index < HARDWARE_SLOTS) {
            if (!moveItemStackTo(stack, HARDWARE_SLOTS, total, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, HARDWARE_SLOTS, false)) {
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
