/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.item;

import dev.jstech.computers.hardware.FormFactor;
import dev.jstech.computers.hardware.MotherboardSpec;
import dev.jstech.computers.hardware.RamGeneration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A motherboard item, the chassis that bounds a build (socket and counts of CPU/RAM/PCIe slots).
 */
public class MotherboardItem extends SpecItem<MotherboardSpec> {

    public MotherboardItem(final Properties properties, final MotherboardSpec spec) {
        super(properties, spec);
    }

    public static boolean fits(final ItemStack stack, final Set<FormFactor> acceptedFormFactors) {
        return stack.getItem() instanceof MotherboardItem board
                && acceptedFormFactors.contains(board.spec().formFactor());
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final MotherboardSpec spec = spec();
        tooltip.add(Component.literal(spec.formFactor().label() + " form factor")
                .withStyle(ChatFormatting.AQUA));
        final String ramTypes = spec.acceptedRam().stream()
                .sorted(Comparator.comparingInt(RamGeneration::ordinal))
                .map(RamGeneration::name)
                .collect(Collectors.joining(" / "));
        /*
         * The board is where a build succeeds or fails, so it spells out exactly what its slots take:
         * the socket, the memory generations, and the bus version cards are held to.
         */
        tooltip.add(Component.literal(
                spec.cpuSlots() + "x " + spec.socket().name() + "  |  "
                        + spec.ramSlots() + " RAM (" + ramTypes + ")  |  "
                        + spec.pcieSlots() + "x " + spec.pcieGeneration())
                .withStyle(ChatFormatting.GRAY));
        HardwareTooltip.appendEra(tooltip, spec.era());
        tooltip.add(Component.literal("Seats parts of its own era only")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
