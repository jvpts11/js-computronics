/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.registry;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative-mode tabs for J's Computronics.
 */
public final class JscCreativeModeTabs {

    private JscCreativeModeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JsComputronics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jsc.main"))
                    .icon(() -> new ItemStack(IndustrialModule.MACERATOR_ITEM.get()))
                    .displayItems((parameters, output) ->
                            output.accept(IndustrialModule.MACERATOR_ITEM.get()))
                    .build());

    public static void register(final IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
