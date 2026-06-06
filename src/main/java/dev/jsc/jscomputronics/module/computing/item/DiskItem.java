/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.DiskSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A storage-disk component item.
 */
public class DiskItem extends Item {

    private final DiskSpec spec;

    public DiskItem(final Properties properties, final DiskSpec spec) {
        super(properties);
        this.spec = spec;
    }

    public DiskSpec spec() {
        return spec;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.literal(
                spec.capacityItems() + " items  (" + spec.capacityMb() + " MB)")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(
                spec.tier() + "  -  " + spec.tier().latencyTicks() + "t latency  -  "
                        + spec.tier().speedMultiplier() + "x speed")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
