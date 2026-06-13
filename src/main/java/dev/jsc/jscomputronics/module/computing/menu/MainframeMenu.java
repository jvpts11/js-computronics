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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Mainframe: the 18 hardware slots (motherboard, CPUs, RAM, GPUs, PSU — each restricted to its component category by the block entity's item handler) plus the player inventory, with powered/capacity/queues/buffer synced for the screen.
 */
public class MainframeMenu extends AbstractComputerMenu {

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

        addPlayerInventory(playerInventory, 8, 182);
        addDataSlots(this.data);
    }

    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.common.tier.HardwareEra hardwareEra() {
        return blockEntity.displayEra();
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

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_AUTOSTART = 1;
    public static final int BUTTON_FAILOVER = 2;

    public boolean isRunning() {
        return data.get(MainframeBlockEntity.DATA_RUNNING) != 0;
    }

    public boolean buildValid() {
        return data.get(MainframeBlockEntity.DATA_BUILD_VALID) != 0;
    }

    public long capacity() {
        return data.get(MainframeBlockEntity.DATA_CAPACITY);
    }

    public int parallelQueues() {
        return data.get(MainframeBlockEntity.DATA_PARALLEL_QUEUES);
    }

    public long ramBuffer() {
        return data.get(MainframeBlockEntity.DATA_RAM_BUFFER);
    }

    public boolean isAutoStart() {
        return data.get(MainframeBlockEntity.DATA_AUTOSTART) != 0;
    }

    public boolean isManualOn() {
        return data.get(MainframeBlockEntity.DATA_MANUAL_ON) != 0;
    }

    public int networkState() {
        return data.get(MainframeBlockEntity.DATA_NETWORK_STATE);
    }

    public int pendingOps() {
        return data.get(MainframeBlockEntity.DATA_PENDING_OPS);
    }

    public int runningOps() {
        return data.get(MainframeBlockEntity.DATA_RUNNING_OPS);
    }

    public int completedOps() {
        return data.get(MainframeBlockEntity.DATA_COMPLETED_OPS);
    }

    public boolean failoverEnabled() {
        return data.get(MainframeBlockEntity.DATA_FAILOVER_ENABLED) != 0;
    }

    public int failoverRole() {
        return data.get(MainframeBlockEntity.DATA_FAILOVER_ROLE);
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
        if (id == BUTTON_FAILOVER) {
            blockEntity.toggleFailover();
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
        return quickMoveBetweenContainerAndPlayer(player, index, HARDWARE_SLOTS);
    }
}
