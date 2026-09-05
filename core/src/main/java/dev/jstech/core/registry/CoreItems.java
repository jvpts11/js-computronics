/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.registry;

import dev.jstech.core.JsCore;
import dev.jstech.core.material.MaterialItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The items the core registers: the material catalogue (the dusts, plates and other forms the mods of the
 * series process and trade). The core has no creative tab of its own; a mod that works the materials shows
 * them in its tab.
 */
public final class CoreItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(JsCore.MODID);

    static {
        // Fills the material catalogue as soon as this class loads, before anything can look an item up.
        MaterialItems.register(ITEMS);
    }

    private CoreItems() {
    }

    public static void register(final IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
