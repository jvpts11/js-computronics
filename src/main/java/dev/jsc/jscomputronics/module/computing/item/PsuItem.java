/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.PsuSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A PSU item.
 */
public class PsuItem extends Item {

    private final PsuSpec spec;

    public PsuItem(final Properties properties, final PsuSpec spec) {
        super(properties);
        this.spec = spec;
    }

    public PsuSpec spec() {
        return spec;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.literal(
                spec.wattage() + " W  -  " + spec.efficiencyPercent() + "% efficient")
                .withStyle(ChatFormatting.GRAY));
    }
}
