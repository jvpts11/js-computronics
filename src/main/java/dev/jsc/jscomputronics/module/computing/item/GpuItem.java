/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.ExpansionCardSpec;
import dev.jsc.jscomputronics.common.hardware.GpuSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A GPU component item.
 */
public class GpuItem extends SpecItem<GpuSpec> implements ExpansionCardItem {

    public GpuItem(final Properties properties, final GpuSpec spec) {
        super(properties, spec);
    }

    @Override
    public ExpansionCardSpec cardSpec() {
        return spec();
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final GpuSpec spec = spec();
        tooltip.add(Component.literal(
                spec.cores() + " cores  -  " + spec.vramMb() + " MB VRAM")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(
                "+1 parallel queue  -  " + spec.tdpWatts() + " W")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(spec.bus().toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
