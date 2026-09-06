/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.item;

import dev.jstech.computronics.hardware.CpuSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A CPU component item.
 */
public class CpuItem extends SpecItem<CpuSpec> {

    public CpuItem(final Properties properties, final CpuSpec spec) {
        super(properties, spec);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final CpuSpec spec = spec();
        tooltip.add(Component.literal(
                spec.cores() + " cores @ " + String.format("%.2f GHz", spec.freqMhz() / 1000.0))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(
                spec.orchestrationCapacity() + " it/t  -  " + spec.tdpWatts() + " W")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Socket " + spec.socket()).withStyle(ChatFormatting.DARK_GRAY));
        // The word size is what an item costs on the era's disks; the player can read the ladder off the chip.
        tooltip.add(Component.literal(spec.era().bits() + "-bit architecture  -  "
                + spec.era().mbPerItem() + " MB per item").withStyle(ChatFormatting.DARK_GRAY));
        HardwareTooltip.appendEra(tooltip, spec.era());
    }
}
