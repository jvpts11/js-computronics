/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * Pattern media: a physical disc that carries crafting patterns as digital data.
 */
public class PatternDiscItem extends Item {

    public static final int DEFAULT_RW_CYCLES = 16;

    private final boolean rewritable;

    public PatternDiscItem(final Properties properties, final boolean rewritable) {
        super(properties.stacksTo(1));
        this.rewritable = rewritable;
    }

    public boolean isRewritable() {
        return rewritable;
    }

    public static List<CraftingPattern> patterns(final ItemStack stack) {
        final List<CraftingPattern> list = stack.get(ComputingModule.DISC_PATTERNS.get());
        return list == null ? List.of() : list;
    }

    public static void append(final ItemStack stack, final CraftingPattern pattern) {
        final List<CraftingPattern> list = new ArrayList<>(patterns(stack));
        list.add(pattern);
        stack.set(ComputingModule.DISC_PATTERNS.get(), List.copyOf(list));
    }

    public static int cyclesLeft(final ItemStack stack) {
        final Integer cycles = stack.get(ComputingModule.DISC_CYCLES.get());
        return cycles == null ? DEFAULT_RW_CYCLES : cycles;
    }

    public boolean canErase(final ItemStack stack) {
        return rewritable && cyclesLeft(stack) > 0 && !patterns(stack).isEmpty();
    }

    public void erase(final ItemStack stack) {
        if (!canErase(stack)) {
            return;
        }
        stack.remove(ComputingModule.DISC_PATTERNS.get());
        stack.set(ComputingModule.DISC_CYCLES.get(), cyclesLeft(stack) - 1);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final int count = patterns(stack).size();
        tooltip.add(Component.literal(count == 1 ? "1 pattern" : count + " patterns")
                .withStyle(ChatFormatting.GRAY));
        if (rewritable) {
            final int cycles = cyclesLeft(stack);
            tooltip.add(Component.literal(cycles > 0 ? cycles + " rewrite cycles left" : "read-only (no cycles left)")
                    .withStyle(cycles > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("write-once").withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
