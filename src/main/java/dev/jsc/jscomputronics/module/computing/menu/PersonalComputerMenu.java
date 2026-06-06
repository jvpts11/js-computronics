/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
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
 * Menu for the Personal Computer.
 */
public class PersonalComputerMenu extends AbstractContainerMenu {

    public static final int TAB_LOCAL = 0;
    public static final int TAB_NETWORK = 1;

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_AUTOSTART = 1;
    public static final int BUTTON_TAB_BASE = 10;

    private static final int HARDWARE_SLOTS = PersonalComputerBlockEntity.HARDWARE_SLOTS;
    private static final int STORAGE_SLOTS = PersonalComputerBlockEntity.STORAGE_SLOTS;
    private static final int COMPUTER_SLOTS = HARDWARE_SLOTS + STORAGE_SLOTS;

    private final PersonalComputerBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private int activeTab = TAB_LOCAL;

    public PersonalComputerMenu(final int containerId, final Inventory playerInventory,
                                final PersonalComputerBlockEntity be) {
        super(ComputingModule.PERSONAL_COMPUTER_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        final IItemHandler hw = be.getHardware();
        addSlot(new TabSlot(hw, PersonalComputerBlockEntity.MOTHERBOARD_SLOT, 8, 32));
        addSlot(new TabSlot(hw, PersonalComputerBlockEntity.PSU_SLOT, 8, 64));
        addSlot(new BoardSlot(hw, PersonalComputerBlockEntity.CPU_SLOT, 44, 32, 0, be::boardCpuSlots));
        for (int i = 0; i < PersonalComputerBlockEntity.RAM_SLOTS; i++) {
            addSlot(new BoardSlot(hw, PersonalComputerBlockEntity.RAM_SLOTS_START + i,
                    44 + i * 18, 64, i, be::boardRamSlots));
        }
        for (int i = 0; i < PersonalComputerBlockEntity.GPU_SLOTS; i++) {
            addSlot(new BoardSlot(hw, PersonalComputerBlockEntity.GPU_SLOTS_START + i,
                    44 + i * 18, 96, i, be::boardPcieSlots));
        }

        final IItemHandler storageHandler = be.getStorage();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            addSlot(new TabSlot(storageHandler, i, 8 + (i % 9) * 18, 124 + (i / 9) * 18));
        }

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    @org.jetbrains.annotations.Nullable
    public static PersonalComputerMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                   final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof PersonalComputerBlockEntity be) {
            return new PersonalComputerMenu(containerId, playerInventory, be);
        }
        return null;
    }

    private void addPlayerInventory(final Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 172 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 232));
        }
    }

    /**
     * A computer slot that is only active (visible, interactive) on the Local tab.
     */
    private final class TabSlot extends SlotItemHandler {
        private TabSlot(final IItemHandler handler, final int index, final int x, final int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return activeTab == TAB_LOCAL;
        }
    }

    /**
     * A Local-tab slot that is only usable while its {@code relativeIndex} is within the count the installed motherboard offers — so CPU/RAM/GPU slots appear and accept parts according to the board, not a fixed maximum.
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
            // Within the board's slot count, or already holding a part — so a
            // component is never trapped behind a smaller board swapped in later.
            return activeTab == TAB_LOCAL && (relativeIndex < boardLimit.getAsInt() || hasItem());
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            return relativeIndex < boardLimit.getAsInt() && super.mayPlace(stack);
        }
    }

    public int activeTab() {
        return activeTab;
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

    public void setActiveTab(final int tab) {
        if (tab == TAB_LOCAL || tab == TAB_NETWORK) {
            activeTab = tab;
        }
    }

    public boolean isRunning() {
        return data.get(0) != 0;
    }

    public boolean buildValid() {
        return data.get(1) != 0;
    }

    public long capacity() {
        return data.get(2);
    }

    public long ramBuffer() {
        return data.get(3);
    }

    public boolean isAutoStart() {
        return data.get(4) != 0;
    }

    public boolean isOnNetwork() {
        return data.get(5) != 0;
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
        if (id >= BUTTON_TAB_BASE) {
            setActiveTab(id - BUTTON_TAB_BASE);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.PERSONAL_COMPUTER.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        // The hardware/storage slots are hidden on the Network tab; don't quick-move
        // items into slots the player cannot see.
        if (activeTab != TAB_LOCAL) {
            return ItemStack.EMPTY;
        }
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int total = slots.size();

        if (index < COMPUTER_SLOTS) {
            if (!moveItemStackTo(stack, COMPUTER_SLOTS, total, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, COMPUTER_SLOTS, false)) {
            // Components land in their hardware slot (category-restricted); anything
            // else falls through to local storage.
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
