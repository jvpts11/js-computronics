/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.ImportBusMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Configuration screen for the Import Bus. All drawing lives in {@link AbstractBusScreen}; this only supplies the title and the filter hint (an empty filter imports everything).
 */
public class ImportBusScreen extends AbstractBusScreen<ImportBusMenu> {

    public ImportBusScreen(final ImportBusMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String windowTitle() {
        return "IMPORT BUS";
    }

    @Override
    protected String filterHint() {
        return "Click an item to import only that type (empty = import everything)";
    }
}
