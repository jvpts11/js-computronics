/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.crafting.RecipeMachines;
import dev.jstech.tests.JsTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The industrial mod tells the Pattern Studio which of its machines run which recipe types through a data
 * file of its own, without either mod knowing the other's classes: the computing mod reads every
 * {@code recipe_machines} file on the server, whatever namespace it comes from.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class IndustrialRecipeMachinesGameTests {

    private IndustrialRecipeMachinesGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void recipeMachines_industrialDataMapsItsMachines(final GameTestHelper helper) {
        // Read the data afresh: another test may have replaced the map in memory.
        RecipeMachines.reload(helper.getLevel().getServer().getResourceManager());

        helper.assertTrue(RecipeMachines.machinesFor("jsindustrial:macerating").equals(List.of("jsindustrial:macerator")),
                "macerating runs on the Macerator; got " + RecipeMachines.machinesFor("jsindustrial:macerating"));
        helper.assertTrue(RecipeMachines.machinesFor("jsindustrial:compressing").equals(List.of("jsindustrial:compressor")),
                "compressing runs on the Compressor; got " + RecipeMachines.machinesFor("jsindustrial:compressing"));
        final List<String> smelting = RecipeMachines.machinesFor("minecraft:smelting");
        helper.assertTrue(smelting.contains("minecraft:furnace") && smelting.contains("jsindustrial:electric_furnace"),
                "smelting keeps the vanilla furnaces and gains the Electric Furnace; got " + smelting);
        /*
         * The vanilla machines stay first whatever order the files were read in: a smelt the network cannot
         * place on a declared machine pairs with the furnace, not with whichever mod loaded first.
         */
        helper.assertTrue(smelting.get(0).equals("minecraft:furnace"),
                "the furnace is the default machine of a vanilla smelt; got " + smelting);
        helper.succeed();
    }
}
