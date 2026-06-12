/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * A slotless menu for the Network Management Studio, mirroring the Command Prompt menu: it carries the host context and lets the server validate that the player has the Studio open for this computer before running a statement.
 */
public class NmsMenu extends AbstractContainerMenu {

    private final BlockPos monitorPos;
    private final BlockPos hostPos;
    private final ContainerLevelAccess access;

    public NmsMenu(final int containerId, final Inventory playerInventory,
                   final BlockPos monitorPos, final BlockPos hostPos) {
        super(ComputingModule.NMS_MENU.get(), containerId);
        this.monitorPos = monitorPos;
        this.hostPos = hostPos;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), hostPos);
    }

    public static NmsMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                      final RegistryFriendlyByteBuf buf) {
        final BlockPos monitor = buf.readBlockPos();
        final BlockPos host = buf.readBlockPos();
        return new NmsMenu(containerId, playerInventory, monitor, host);
    }

    public BlockPos monitorPos() {
        return monitorPos;
    }

    public BlockPos hostPos() {
        return hostPos;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(final Player player) {
        return access.evaluate((level, pos) ->
                level.getBlockEntity(pos) instanceof ComputerTerminalHost
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }
}
