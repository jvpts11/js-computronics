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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Personal Computer: a single hardware-assembly surface (motherboard, PSU, CPU, RAM, GPU, disks — each restricted to its component category and clamped to the count the installed motherboard offers) plus the player inventory.
 */
public class PersonalComputerMenu extends AbstractComputerMenu {

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_AUTOSTART = 1;

    private static final int HARDWARE_SLOTS = PersonalComputerBlockEntity.HARDWARE_SLOTS;

    private final PersonalComputerBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public PersonalComputerMenu(final int containerId, final Inventory playerInventory,
                                final PersonalComputerBlockEntity be) {
        super(ComputingModule.PERSONAL_COMPUTER_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        final IItemHandler hw = be.getHardware();
        addSlot(new SlotItemHandler(hw, PersonalComputerBlockEntity.MOTHERBOARD_SLOT, 8, 40));
        addSlot(new SlotItemHandler(hw, PersonalComputerBlockEntity.PSU_SLOT, 8, 73));
        addSlot(new BoardSlot(hw, PersonalComputerBlockEntity.CPU_SLOT, 44, 40, 0, be::boardCpuSlots));
        for (int i = 0; i < PersonalComputerBlockEntity.RAM_SLOTS; i++) {
            addSlot(new BoardSlot(hw, PersonalComputerBlockEntity.RAM_SLOTS_START + i,
                    44 + i * 18, 73, i, be::boardRamSlots));
        }
        for (int i = 0; i < PersonalComputerBlockEntity.GPU_SLOTS; i++) {
            addSlot(new BoardSlot(hw, PersonalComputerBlockEntity.GPU_SLOTS_START + i,
                    44 + i * 18, 106, i, be::boardPcieSlots));
        }
        for (int i = 0; i < PersonalComputerBlockEntity.DISK_SLOTS; i++) {
            addSlot(new BoardSlot(hw, PersonalComputerBlockEntity.DISK_SLOTS_START + i,
                    8 + i * 18, 106, i, be::boardDiskSlots));
        }

        addPlayerInventory(playerInventory, 8, 138);
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

    public net.minecraft.core.BlockPos pcPos() {
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
    public dev.jsc.jscomputronics.common.tier.HardwareEra hardwareEra() {
        return blockEntity.installedEra();
    }

    public boolean hasBoard() {
        return slots.get(0).hasItem();
    }

    public boolean hasPsu() {
        return slots.get(1).hasItem();
    }

    public boolean isRunning() {
        return data.get(PersonalComputerBlockEntity.DATA_RUNNING) != 0;
    }

    public boolean buildValid() {
        return data.get(PersonalComputerBlockEntity.DATA_BUILD_VALID) != 0;
    }

    public long capacity() {
        return data.get(PersonalComputerBlockEntity.DATA_CAPACITY);
    }

    public long ramBuffer() {
        return data.get(PersonalComputerBlockEntity.DATA_RAM_BUFFER);
    }

    public boolean isAutoStart() {
        return data.get(PersonalComputerBlockEntity.DATA_AUTOSTART) != 0;
    }

    public boolean isOnNetwork() {
        return data.get(PersonalComputerBlockEntity.DATA_ON_NETWORK) != 0;
    }

    public int networkServerCount() {
        return data.get(PersonalComputerBlockEntity.DATA_SERVER_COUNT);
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
        return stillValid(access, player, ComputingModule.PERSONAL_COMPUTER.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMoveBetweenContainerAndPlayer(player, index, HARDWARE_SLOTS);
    }
}
