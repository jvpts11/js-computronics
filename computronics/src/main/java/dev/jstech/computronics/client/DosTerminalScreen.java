/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.DosTerminalMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The MC-DOS terminal: the whole monitor glass is the console, no program window around it, the machine IS
 * the prompt. Boots with the period MC-DOS banner and the drive-tracking {@code C:\>} prompt. The MC-NET
 * Command Prompt window is deliberately not reused here; each platform owns its console screen.
 */
public final class DosTerminalScreen extends CommandPromptScreen<DosTerminalMenu> {

    public DosTerminalScreen(final DosTerminalMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean dosStyle() {
        return true;
    }

    @Override
    protected boolean bareTerminal() {
        return true;
    }
}
