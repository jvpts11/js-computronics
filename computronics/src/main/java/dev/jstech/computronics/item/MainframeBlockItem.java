/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Block item for the Mainframe: the cabinet's own model in the slot, plus the machine's tooltip.
 */
public class MainframeBlockItem extends CabinetBlockItem {

    /**
     * How a Mainframe cabinet sits in an item slot: 3 blocks wide is its longest side, and its model
     * spans x -1.5..1.5, y 0..2, z -0.5..1.5 blocks around the controller, which sits in the middle of
     * the bottom front row, so only the height and the depth need re-centring.
     */
    private static final Fit MAINFRAME_FIT = new Fit(48.0F, 0.0F, -1.0F, -0.5F);

    public MainframeBlockItem(final Block block, final Item.Properties properties, final String model) {
        super(block, properties, "mainframe", model, "mainframe", MAINFRAME_FIT);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jsc.mainframe.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
