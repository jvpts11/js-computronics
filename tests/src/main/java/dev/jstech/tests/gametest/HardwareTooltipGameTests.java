/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.HardwareItems;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.os.MinSpecTooltip;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.tests.JsTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A part's era reads in colour before it reads as a word: the green of a CRT for Vintage, the Legacy
 * desktop's blue for Legacy, the Standard desktop's accent blue for Standard, on every hardware tooltip
 * and on the "needs ... hardware" line of an install disc.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class HardwareTooltipGameTests {

    private HardwareTooltipGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void eraLine_wearsItsErasScreenColour(final GameTestHelper helper) {
        assertEraColour(helper, new ItemStack(HardwareItems.CPU_INTEGRA_486SX.get()), HardwareEra.VINTAGE);
        assertEraColour(helper, new ItemStack(HardwareItems.CPU_INTEGRA_VERTEX_700.get()), HardwareEra.LEGACY);
        assertEraColour(helper, new ItemStack(HardwareItems.CPU_APEX_5_4590.get()), HardwareEra.STANDARD);
        assertEraColour(helper, new ItemStack(HardwareItems.RAM_SIMM_4.get()), HardwareEra.VINTAGE);
        assertEraColour(helper, new ItemStack(HardwareItems.GPU_VERTEX_GTX_780_TI.get()), HardwareEra.STANDARD);
        helper.assertTrue(HardwareEra.VINTAGE.screenColor() != HardwareEra.LEGACY.screenColor()
                        && HardwareEra.LEGACY.screenColor() != HardwareEra.STANDARD.screenColor(),
                "the three eras tell apart by colour");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void installDiscNeedsLine_coloursTheEraTheSameWay(final GameTestHelper helper) {
        final List<Component> lines = MinSpecTooltip.osMinSpec(
                ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "fedora"));
        helper.assertTrue(!lines.isEmpty() && lines.get(0).getString().equals("Needs Legacy hardware or later"),
                "the needs line reads as before; got " + (lines.isEmpty() ? "nothing" : lines.get(0).getString()));
        final TextColor colour = lines.get(0).getSiblings().get(0).getStyle().getColor();
        helper.assertTrue(colour != null && colour.getValue() == HardwareEra.LEGACY.screenColor(),
                "the era word carries the Legacy screen colour; got " + colour);
        helper.succeed();
    }

    private static void assertEraColour(final GameTestHelper helper, final ItemStack stack, final HardwareEra era) {
        final List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null,
                TooltipFlag.NORMAL);
        for (final Component line : tooltip) {
            if (line.getString().endsWith(" era")) {
                final TextColor colour = line.getStyle().getColor();
                helper.assertTrue(colour != null && colour.getValue() == era.screenColor(),
                        stack.getHoverName().getString() + ": the era line is " + colour + ", not " + era + "'s colour");
                return;
            }
        }
        helper.fail(stack.getHoverName().getString() + " has no era line in its tooltip");
    }
}
