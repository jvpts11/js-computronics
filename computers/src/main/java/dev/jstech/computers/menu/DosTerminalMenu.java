/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.menu;

import dev.jstech.computers.ComputingModule;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * The MC-DOS terminal: the monitor of a computer that booted MC-DOS. Same command plumbing as the other
 * terminals, but its own menu type so the client opens the dedicated full-screen DOS terminal instead of
 * the MC-NET Command Prompt window.
 */
public class DosTerminalMenu extends CommandPromptMenu {

    public DosTerminalMenu(final int containerId, final Inventory playerInventory, final BlockPos monitorPos,
                           final BlockPos hostPos, @Nullable final HardwareEra era, final String shellId,
                           final String hostname, final String osLabel) {
        super(ComputingModule.DOS_TERMINAL_MENU.get(), containerId, playerInventory, monitorPos, hostPos,
                era, shellId, hostname, osLabel);
    }

    public static DosTerminalMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                              final RegistryFriendlyByteBuf buf) {
        final OpenData data = readOpenBuffer(buf);
        return new DosTerminalMenu(containerId, playerInventory, data.monitor(), data.host(), data.era(),
                data.shellId(), data.hostname(), data.osLabel());
    }
}
