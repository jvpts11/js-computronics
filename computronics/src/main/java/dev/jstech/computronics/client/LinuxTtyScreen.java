/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.LinuxTtyMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Linux virtual console: a full-glass TTY, the way a distribution without a desktop environment (or a
 * booted live installer) really presents itself: a getty banner, a login, a shell, nothing else. The MC-NET
 * Command Prompt window is deliberately not reused here; each platform owns its console screen.
 */
public final class LinuxTtyScreen extends CommandPromptScreen<LinuxTtyMenu> {

    public LinuxTtyScreen(final LinuxTtyMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean bareTerminal() {
        return true;
    }
}
