/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.RamSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A RAM module item.
 */
public class RamItem extends Item {

    private final RamSpec spec;

    public RamItem(final Properties properties, final RamSpec spec) {
        super(properties);
        this.spec = spec;
    }

    public RamSpec spec() {
        return spec;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.literal(
                spec.bufferItems() + " items buffer  -  " + spec.generation())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(spec.tdpWatts() + " W").withStyle(ChatFormatting.DARK_GRAY));
    }
}
