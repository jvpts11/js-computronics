/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity;
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
 * Menu for a Supercomputer Node's assembly surface: server board, CPU, RAM, the co-processor bay, PSU and disks — a full computer, assembled like any other.
 */
public class SupercomputerNodeMenu extends AbstractContainerMenu {

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_AUTOSTART = 1;

    private static final int HARDWARE_SLOTS = SupercomputerNodeBlockEntity.HARDWARE_SLOTS;

    private final SupercomputerNodeBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public SupercomputerNodeMenu(final int containerId, final Inventory playerInventory,
                                 final SupercomputerNodeBlockEntity be) {
        super(ComputingModule.SUPERCOMPUTER_NODE_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        final IItemHandler hw = be.getHardware();
        addSlot(new SlotItemHandler(hw, SupercomputerNodeBlockEntity.MOTHERBOARD_SLOT, 8, 40));
        addSlot(new BoardSlot(hw, SupercomputerNodeBlockEntity.CPU_SLOT, 44, 40, 0, be::boardCpuSlots));
        for (int i = 0; i < SupercomputerNodeBlockEntity.RAM_SLOTS; i++) {
            addSlot(new BoardSlot(hw, SupercomputerNodeBlockEntity.RAM_SLOTS_START + i,
                    80 + i * 18, 40, i, be::boardRamSlots));
        }
        addSlot(new BoardSlot(hw, SupercomputerNodeBlockEntity.PHI_SLOT, 8, 73, 0, be::boardPcieSlots));
        addSlot(new SlotItemHandler(hw, SupercomputerNodeBlockEntity.PSU_SLOT, 44, 73));
        for (int i = 0; i < SupercomputerNodeBlockEntity.DISK_SLOTS; i++) {
            addSlot(new BoardSlot(hw, SupercomputerNodeBlockEntity.DISK_SLOTS_START + i,
                    80 + i * 18, 73, i, be::boardDiskSlots));
        }

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    @org.jetbrains.annotations.Nullable
    public static SupercomputerNodeMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                    final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof SupercomputerNodeBlockEntity be) {
            return new SupercomputerNodeMenu(containerId, playerInventory, be);
        }
        return null;
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

    /**
     * A hardware slot active only while the installed board offers its index (or it holds a part).
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
            return relativeIndex < boardLimit.getAsInt() || hasItem();
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            return relativeIndex < boardLimit.getAsInt() && super.mayPlace(stack);
        }
    }

    public net.minecraft.core.BlockPos computerPos() {
        return blockEntity.getBlockPos();
    }

    public String customName() {
        return blockEntity.customName();
    }

    public boolean hasBoard() {
        return slots.get(0).hasItem();
    }

    public boolean hasPhi() {
        return slots.get(4).hasItem();
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

    public boolean isRunning() {
        return data.get(SupercomputerNodeBlockEntity.DATA_RUNNING) != 0;
    }

    public boolean buildValid() {
        return data.get(SupercomputerNodeBlockEntity.DATA_BUILD_VALID) != 0;
    }

    public boolean isAutoStart() {
        return data.get(SupercomputerNodeBlockEntity.DATA_AUTOSTART) != 0;
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
        return stillValid(access, player, ComputingModule.SUPERCOMPUTER_NODE.get());
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
