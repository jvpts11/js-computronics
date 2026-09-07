/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.item;

import dev.jstech.computronics.hardware.ClusterInterfaceCardSpec;
import dev.jstech.computronics.hardware.IExpansionCardSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The Cluster Interface Card family: exclusive to the Cluster Management Computer, one per machine.
 * The card decides which cabinets the machine can drive and how many nodes it installs at once.
 */
public class ClusterInterfaceCardItem extends SpecItem<ClusterInterfaceCardSpec> implements IExpansionCardItem {

    public ClusterInterfaceCardItem(final Properties properties, final ClusterInterfaceCardSpec spec) {
        super(properties.stacksTo(16), spec);
    }

    @Override
    public IExpansionCardSpec cardSpec() {
        return spec();
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final ClusterInterfaceCardSpec spec = spec();
        tooltip.add(Component.literal("Cluster interface for a Cluster Management Computer")
                .withStyle(ChatFormatting.GRAY));
        final String reach = switch (spec.reach()) {
            case DATACENTERS -> "datacenters";
            case SUPERCOMPUTERS -> "datacenters and supercomputers";
            case ALL -> "datacenters, supercomputers and AI clusters";
        };
        tooltip.add(Component.literal("Reaches  -  " + reach).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Installs  -  " + spec.parallelNodes()
                + (spec.parallelNodes() == 1 ? " node at a time" : " nodes in parallel"))
                .withStyle(ChatFormatting.GRAY));
        HardwareTooltip.appendEra(tooltip, spec.era());
        tooltip.add(Component.literal(spec.tier() + "  -  " + spec.tdpWatts() + " W  -  " + spec.bus())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
