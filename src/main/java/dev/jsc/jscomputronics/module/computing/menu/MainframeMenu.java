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
        addSlot(new SlotItemHandler(hardware, MainframeBlockEntity.MOTHERBOARD_SLOT, 8, 40));
        addSlot(new SlotItemHandler(hardware, MainframeBlockEntity.PSU_SLOT, 8, 73));
        for (int i = 0; i < MainframeBlockEntity.CPU_SLOTS; i++) {
            addSlot(new BoardSlot(hardware, MainframeBlockEntity.CPU_SLOTS_START + i,
                    44 + i * 18, 40, i, be::boardCpuSlots));
        }
        for (int i = 0; i < MainframeBlockEntity.RAM_SLOTS; i++) {
            addSlot(new BoardSlot(hardware, MainframeBlockEntity.RAM_SLOTS_START + i,
                    44 + (i % 4) * 18, 73 + (i / 4) * 18, i, be::boardRamSlots));
        }
        for (int i = 0; i < MainframeBlockEntity.GPU_SLOTS; i++) {
            addSlot(new BoardSlot(hardware, MainframeBlockEntity.GPU_SLOTS_START + i,
                    44 + (i % 3) * 18, 124 + (i / 3) * 18, i, be::boardPcieSlots));
        }
        for (int i = 0; i < MainframeBlockEntity.DISK_SLOTS; i++) {
            addSlot(new BoardSlot(hardware, MainframeBlockEntity.DISK_SLOTS_START + i,
                    8 + (i % 2) * 18, 124 + (i / 2) * 18, i, be::boardDiskSlots));
        }

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    /**
     * A hardware slot usable only while its {@code relativeIndex} is within the count the installed motherboard offers — so CPU/RAM/PCIe slots appear and accept parts according to the board, exactly like the Personal Computer.
     */
    private final class BoardSlot extends SlotItemHandler {
        private final int relativeIndex;
        private final java.util.function.IntSupplier boardLimit;

        private BoardSlot(final IItemHandler handler, final int index, final int x, final int y,
                          final int relativeIndex, final java.util.function.IntSupplier boardLimit) {
            super(handler, index, x, y);
            this.relativeIndex = relativeIndex;
            this.boardLimit = boardLimit;
        }

        @Override
        public boolean isActive() {
            // Within the board's slot count, or already holding a part — so a part
            // is never trapped behind a smaller board swapped in later.
            return relativeIndex < boardLimit.getAsInt() || hasItem();
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            return relativeIndex < boardLimit.getAsInt() && super.mayPlace(stack);
        }
    }

    public boolean hasBoard() {
        return slots.get(0).hasItem();
    }

    public boolean hasPsu() {
        return slots.get(1).hasItem();
    }

    public net.minecraft.core.BlockPos blockPos() {
        return blockEntity.getBlockPos();
    }

    public int boardCpuSlots() {
        return blockEntity.boardCpuSlots();
    }

    public int boardRamSlots() {
        return blockEntity.boardRamSlots();
    }

    public int boardPcieSlots() {
        return blockEntity.boardPcieSlots();
    }

    public int boardDiskSlots() {
        return blockEntity.boardDiskSlots();
    }

    @org.jetbrains.annotations.Nullable
    public static MainframeMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                            final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof MainframeBlockEntity be) {
            return new MainframeMenu(containerId, playerInventory, be);
        }
        return null;
    }

    private void addPlayerInventory(final Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 182 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 240));
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
