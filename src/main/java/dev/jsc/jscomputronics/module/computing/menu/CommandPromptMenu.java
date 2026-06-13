/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A slotless menu for the Command Prompt. It holds no inventory — the console is driven entirely by command payloads — but being a real menu lets the server validate that the player has this prompt open for this host before running a typed line, exactly as the graphical terminal does.
 */
public class CommandPromptMenu extends AbstractContainerMenu {

    private final BlockPos monitorPos;
    private final BlockPos hostPos;
    @Nullable
    private final HardwareEra era;
    private final ContainerLevelAccess access;

    public CommandPromptMenu(final int containerId, final Inventory playerInventory,
                             final BlockPos monitorPos, final BlockPos hostPos,
                             @Nullable final HardwareEra era) {
        super(ComputingModule.COMMAND_PROMPT_MENU.get(), containerId);
        this.monitorPos = monitorPos;
        this.hostPos = hostPos;
        this.era = era;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), hostPos);
    }

    public static CommandPromptMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                final RegistryFriendlyByteBuf buf) {
        final BlockPos monitor = buf.readBlockPos();
        final BlockPos host = buf.readBlockPos();
        final int eraOrdinal = buf.readVarInt();
        final HardwareEra era = eraOrdinal >= 0 && eraOrdinal < HardwareEra.values().length
                ? HardwareEra.values()[eraOrdinal] : null;
        return new CommandPromptMenu(containerId, playerInventory, monitor, host, era);
    }

    /** Writes the open buffer the client reconstructs from: the two positions plus the host era ordinal (-1 if none). */
    public static void writeOpenBuffer(final RegistryFriendlyByteBuf buf, final BlockPos monitorPos,
                                       final BlockPos hostPos, @Nullable final HardwareEra era) {
        buf.writeBlockPos(monitorPos);
        buf.writeBlockPos(hostPos);
        buf.writeVarInt(era == null ? -1 : era.ordinal());
    }

    public BlockPos monitorPos() {
        return monitorPos;
    }

    public BlockPos hostPos() {
        return hostPos;
    }

    /** The host computer's board-derived hardware era, captured when the prompt was opened; {@code null} for none. */
    @Nullable
    public HardwareEra hardwareEra() {
        return era;
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
