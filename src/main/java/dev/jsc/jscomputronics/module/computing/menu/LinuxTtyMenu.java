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
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * The Linux virtual console: the monitor of a distribution without a desktop environment, and of a booted
 * Arch/Gentoo live medium. Same command plumbing as the other terminals, but its own menu type so the
 * client opens the dedicated full-screen TTY instead of the MC-NET Command Prompt window.
 */
public class LinuxTtyMenu extends CommandPromptMenu {

    public LinuxTtyMenu(final int containerId, final Inventory playerInventory, final BlockPos monitorPos,
                        final BlockPos hostPos, @Nullable final HardwareEra era, final String shellId,
                        final String hostname, final String osLabel) {
        super(ComputingModule.LINUX_TTY_MENU.get(), containerId, playerInventory, monitorPos, hostPos,
                era, shellId, hostname, osLabel);
    }

    public static LinuxTtyMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                           final RegistryFriendlyByteBuf buf) {
        final OpenData data = readOpenBuffer(buf);
        return new LinuxTtyMenu(containerId, playerInventory, data.monitor(), data.host(), data.era(),
                data.shellId(), data.hostname(), data.osLabel());
    }
}
