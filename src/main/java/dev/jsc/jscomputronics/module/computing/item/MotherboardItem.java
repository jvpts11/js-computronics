/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.FormFactor;
import dev.jsc.jscomputronics.common.hardware.MotherboardSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Set;

/**
 * A motherboard item — the chassis that bounds a build (socket and counts of CPU/RAM/PCIe slots).
 */
public class MotherboardItem extends Item {

    private final MotherboardSpec spec;

    public MotherboardItem(final Properties properties, final MotherboardSpec spec) {
        super(properties);
        this.spec = spec;
    }

    public MotherboardSpec spec() {
        return spec;
    }

    public static boolean fits(final ItemStack stack, final Set<FormFactor> acceptedFormFactors) {
        return stack.getItem() instanceof MotherboardItem board
                && acceptedFormFactors.contains(board.spec().formFactor());
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.literal(spec.formFactor().label() + " form factor")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(
                spec.cpuSlots() + "x " + spec.socket() + "  -  "
                        + spec.ramSlots() + " RAM  -  " + spec.pcieSlots() + " PCIe")
                .withStyle(ChatFormatting.GRAY));
    }
}
