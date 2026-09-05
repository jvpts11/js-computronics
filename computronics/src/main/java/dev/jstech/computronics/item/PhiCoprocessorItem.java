/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.item;

import dev.jstech.computronics.hardware.ExpansionCardSpec;
import dev.jstech.computronics.hardware.PhiCoprocessorSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A crafting co-processor item for the Supercomputer.
 */
public class PhiCoprocessorItem extends SpecItem<PhiCoprocessorSpec> implements ExpansionCardItem {

    public PhiCoprocessorItem(final Properties properties, final PhiCoprocessorSpec spec) {
        super(properties.stacksTo(16), spec);
    }

    @Override
    public ExpansionCardSpec cardSpec() {
        return spec();
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final PhiCoprocessorSpec spec = spec();
        tooltip.add(Component.literal(spec.cores() + " cores @ "
                        + String.format(java.util.Locale.ROOT, "%.2f", spec.mhz() / 1000.0) + " GHz")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Supercomputer slots 1-" + spec.maxSlot())
                .withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
