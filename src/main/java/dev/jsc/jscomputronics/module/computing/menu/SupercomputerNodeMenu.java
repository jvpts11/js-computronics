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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for a Supercomputer Node's assembly surface: server board, CPU, RAM, the co-processor bay, PSU and disks — a full computer, assembled like any other.
 */
public class SupercomputerNodeMenu extends AbstractComputerMenu {

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

        addPlayerInventory(playerInventory, 8, 138);
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

    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.common.tier.HardwareEra hardwareEra() {
        return blockEntity.installedEra();
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
        return quickMoveBetweenContainerAndPlayer(player, index, HARDWARE_SLOTS);
    }
}
