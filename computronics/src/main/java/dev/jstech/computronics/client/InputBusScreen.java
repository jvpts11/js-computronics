/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.InputBusMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Configuration screen for the Input Bus. All drawing lives in {@link AbstractBusScreen}; this only supplies
 * the title and the filter hint.
 */
public class InputBusScreen extends AbstractBusScreen<InputBusMenu> {

    public InputBusScreen(final InputBusMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String windowTitle() {
        return "CRAFTING INPUT BUS";
    }

    @Override
    protected String filterHint() {
        return "Click an item to set what to feed the machine";
    }
}
