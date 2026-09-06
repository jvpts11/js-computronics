/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.menu;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity;
import dev.jstech.computronics.gui.layout.ClusterManagementComputerLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** The Cluster Management Computer's assembly menu: the hardware slots and the screen's numbers. */
public class ClusterManagementComputerMenu extends AbstractComputerMenu {

    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_AUTOSTART = 1;

    private static final int HARDWARE_SLOTS = ClusterManagementComputerBlockEntity.HARDWARE_SLOTS;

    private final ClusterManagementComputerBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public ClusterManagementComputerMenu(final int containerId, final Inventory playerInventory,
                                         final ClusterManagementComputerBlockEntity be) {
        super(ComputingModule.CLUSTER_MANAGEMENT_COMPUTER_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        final IItemHandler hw = be.getHardware();
        final int slot = ClusterManagementComputerLayout.SLOT;
        addSlot(new SlotItemHandler(hw, ClusterManagementComputerBlockEntity.MOTHERBOARD_SLOT,
                ClusterManagementComputerLayout.MOBO_X, ClusterManagementComputerLayout.MOBO_Y));
        addSlot(new SlotItemHandler(hw, ClusterManagementComputerBlockEntity.PSU_SLOT,
                ClusterManagementComputerLayout.PSU_X, ClusterManagementComputerLayout.PSU_Y));
        addSlot(new BoardSlot(hw, ClusterManagementComputerBlockEntity.CPU_SLOT,
                ClusterManagementComputerLayout.RIGHT_X, ClusterManagementComputerLayout.CPU_Y, 0, be::boardCpuSlots));
        for (int i = 0; i < ClusterManagementComputerBlockEntity.RAM_SLOTS; i++) {
            addSlot(new BoardSlot(hw, ClusterManagementComputerBlockEntity.RAM_SLOTS_START + i,
                    ClusterManagementComputerLayout.RIGHT_X + i * slot, ClusterManagementComputerLayout.RAM_Y, i,
                    be::boardRamSlots));
        }
        for (int i = 0; i < ClusterManagementComputerBlockEntity.PCIE_SLOTS; i++) {
            addSlot(new BoardSlot(hw, ClusterManagementComputerBlockEntity.PCIE_SLOTS_START + i,
                    ClusterManagementComputerLayout.RIGHT_X + i * slot, ClusterManagementComputerLayout.PCIE_Y, i,
                    be::boardPcieSlots));
        }
        for (int i = 0; i < ClusterManagementComputerBlockEntity.DISK_SLOTS; i++) {
            addSlot(new BoardSlot(hw, ClusterManagementComputerBlockEntity.DISK_SLOTS_START + i,
                    ClusterManagementComputerLayout.MOBO_X + i * slot, ClusterManagementComputerLayout.DISK_Y, i,
                    be::boardDiskSlots));
        }
        addPlayerInventory(playerInventory, ClusterManagementComputerLayout.INV_X, ClusterManagementComputerLayout.INV_Y);
        addDataSlots(this.data);
    }

    @Nullable
    public static ClusterManagementComputerMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                            final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof ClusterManagementComputerBlockEntity be) {
            return new ClusterManagementComputerMenu(containerId, playerInventory, be);
        }
        return null;
    }

    public BlockPos computerPos() {
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

    @Nullable
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
        return data.get(ClusterManagementComputerBlockEntity.DATA_RUNNING) != 0;
    }

    public boolean buildValid() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_BUILD_VALID) != 0;
    }

    public long capacity() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_CAPACITY);
    }

    public long ramBuffer() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_RAM_BUFFER);
    }

    public boolean isAutoStart() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_AUTOSTART) != 0;
    }

    public boolean isOnNetwork() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_ON_NETWORK) != 0;
    }

    public boolean hasCard() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_HAS_CARD) != 0;
    }

    public int supercomputers() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_SUPERCOMPUTERS);
    }

    public int datacenters() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_DATACENTERS);
    }

    public int lanes() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_LANES);
    }

    public boolean managerInstalled() {
        return data.get(ClusterManagementComputerBlockEntity.DATA_MANAGER_INSTALLED) != 0;
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
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMoveBetweenContainerAndPlayer(player, index, HARDWARE_SLOTS);
    }

    @Override
    public boolean stillValid(final Player player) {
        return access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock()
                        instanceof dev.jstech.computronics.block.ClusterManagementComputerBlock
                        && player.canInteractWithBlock(pos, 4.0), true);
    }
}
