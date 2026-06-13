/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.CraftingCardSpec;
import dev.jsc.jscomputronics.common.hardware.ExpansionCardSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A Crafting Card component item: the PCIe card a Crafting Computer needs to execute recipes.
 */
public class CraftingCardItem extends SpecItem<CraftingCardSpec> implements ExpansionCardItem {

    public CraftingCardItem(final Properties properties, final CraftingCardSpec spec) {
        super(properties, spec);
    }

    @Override
    public ExpansionCardSpec cardSpec() {
        return spec();
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final CraftingCardSpec spec = spec();
        tooltip.add(Component.literal("Executes recipes  -  " + spec.cpuFactor() + "x CPU")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(spec.tier() + "  -  " + spec.tdpWatts() + " W")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(spec.bus().toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
