/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.menu;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.gui.layout.CraftingComputerLayout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Crafting Computer's assembly surface: motherboard, PSU, CPU, RAM, PCIe (where the Crafting Card goes) and disks (each restricted to its component category and clamped to the count the installed motherboard offers) plus the player inventory.
 */
public class CraftingComputerMenu extends AbstractComputerMenu {

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_AUTOSTART = 1;

    private static final int HARDWARE_SLOTS = CraftingComputerBlockEntity.HARDWARE_SLOTS;

    private final CraftingComputerBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public CraftingComputerMenu(final int containerId, final Inventory playerInventory,
                                final CraftingComputerBlockEntity be) {
        super(ComputingModule.CRAFTING_COMPUTER_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        final IItemHandler hw = be.getHardware();
        addSlot(new SlotItemHandler(hw, CraftingComputerBlockEntity.MOTHERBOARD_SLOT, 8, 40));
        addSlot(new SlotItemHandler(hw, CraftingComputerBlockEntity.PSU_SLOT, 8, 73));
        addSlot(new BoardSlot(hw, CraftingComputerBlockEntity.CPU_SLOT, 44, 40, 0, be::boardCpuSlots));
        for (int i = 0; i < CraftingComputerBlockEntity.RAM_SLOTS; i++) {
            addSlot(new BoardSlot(hw, CraftingComputerBlockEntity.RAM_SLOTS_START + i,
                    44 + i * 18, 73, i, be::boardRamSlots));
        }
        for (int i = 0; i < CraftingComputerBlockEntity.PCIE_SLOTS; i++) {
            addSlot(new BoardSlot(hw, CraftingComputerBlockEntity.PCIE_SLOTS_START + i,
                    44 + i * 18, 106, i, be::boardPcieSlots));
        }
        for (int i = 0; i < CraftingComputerBlockEntity.DISK_SLOTS; i++) {
            addSlot(new BoardSlot(hw, CraftingComputerBlockEntity.DISK_SLOTS_START + i,
                    8 + i * 18, 106, i, be::boardDiskSlots));
        }

        addPlayerInventory(playerInventory, CraftingComputerLayout.INV_X, CraftingComputerLayout.INV_Y);
        addDataSlots(this.data);
    }

    @org.jetbrains.annotations.Nullable
    public static CraftingComputerMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                   final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof CraftingComputerBlockEntity be) {
            return new CraftingComputerMenu(containerId, playerInventory, be);
        }
        return null;
    }

    public net.minecraft.core.BlockPos computerPos() {
        return blockEntity.getBlockPos();
    }

    public String customName() {
        return blockEntity.customName();
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
    public dev.jstech.core.tier.HardwareEra hardwareEra() {
        return blockEntity.displayEra();
    }

    public boolean hasBoard() {
        return slots.get(0).hasItem();
    }

    public boolean hasPsu() {
        return slots.get(1).hasItem();
    }

    public boolean isRunning() {
        return data.get(CraftingComputerBlockEntity.DATA_RUNNING) != 0;
    }

    public boolean buildValid() {
        return data.get(CraftingComputerBlockEntity.DATA_BUILD_VALID) != 0;
    }

    public long capacity() {
        return data.get(CraftingComputerBlockEntity.DATA_CAPACITY);
    }

    public long ramBuffer() {
        return data.get(CraftingComputerBlockEntity.DATA_RAM_BUFFER);
    }

    public boolean isAutoStart() {
        return data.get(CraftingComputerBlockEntity.DATA_AUTOSTART) != 0;
    }

    public boolean isOnNetwork() {
        return data.get(CraftingComputerBlockEntity.DATA_ON_NETWORK) != 0;
    }

    public int craftFactorX100() {
        return data.get(CraftingComputerBlockEntity.DATA_CRAFT_FACTOR_X100);
    }

    public long craftThroughput() {
        return data.get(CraftingComputerBlockEntity.DATA_CRAFT_THROUGHPUT);
    }

    public int craftThreads() {
        return data.get(CraftingComputerBlockEntity.DATA_CRAFT_THREADS);
    }

    public int romUsed() {
        return data.get(CraftingComputerBlockEntity.DATA_ROM_USED);
    }

    public int romLimit() {
        return CraftingComputerBlockEntity.RECIPE_ROM_LIMIT;
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
        /*
         * Validate against the block family, not a single block: the Standard, Vintage and Legacy
         * Crafting Computers are distinct blocks that share this menu. Checking only the Standard block
         * would make the server reject a Vintage/Legacy menu as invalid and close it the instant it opens.
         */
        return access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock()
                        instanceof dev.jstech.computers.block.CraftingComputerBlock
                        && player.canInteractWithBlock(pos, 4.0), true);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMoveBetweenContainerAndPlayer(player, index, HARDWARE_SLOTS);
    }
}
