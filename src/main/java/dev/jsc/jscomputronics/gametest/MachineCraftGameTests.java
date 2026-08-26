/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern.ProcessingInput;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern.ProcessingOutput;
import dev.jsc.jscomputronics.module.computing.os.fs.CraftFile;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests for the machine-crafting data model: processing/multi-stage patterns serialize to and from {@code
 * .craft} without loss (chances, timeout and machine survive), and load into a Crafting Computer's machine ROM
 * with dedupe and the shared slot budget.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class MachineCraftGameTests {

    private MachineCraftGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void processingPattern_roundTripsAndLoadsIntoRom(final GameTestHelper helper) {
        final var registries = helper.getLevel().registryAccess();
        final ProcessingPattern pattern = new ProcessingPattern(
                List.of(new ProcessingInput(StorageKey.of(Items.IRON_INGOT), 1L)),
                List.of(new ProcessingOutput(StorageKey.of(Items.COPPER_INGOT), 2L, 100),
                        new ProcessingOutput(StorageKey.of(Items.GOLD_NUGGET), 1L, 50)),
                "Macerator", 200);

        final String snbt = CraftFile.serializeProcessing(pattern, registries).orElseThrow();
        helper.assertTrue("proc".equals(CraftFile.typeOf(snbt)),
                "the tagged type is proc; got " + CraftFile.typeOf(snbt));
        final ProcessingPattern parsed = CraftFile.parseProcessing(snbt, registries).orElseThrow();
        helper.assertTrue(parsed.sameRecipe(pattern), "the .craft round-trip preserves the recipe");
        helper.assertTrue(parsed.outputs().get(1).chancePercent() == 50, "the 50% output chance survives");
        helper.assertTrue(parsed.timeoutTicks() == 200, "the timeout survives");

        final BlockPos ccPos = new BlockPos(2, 2, 2);
        helper.setBlock(ccPos, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(ccPos) instanceof CraftingComputerBlockEntity be)) {
            helper.fail("no Crafting Computer block entity");
            return;
        }
        final NetworkRecipe recipe = NetworkRecipe.ofProcessing(pattern);
        helper.assertTrue(be.loadMachineRecipe(recipe), "the machine recipe loads into the ROM");
        helper.assertTrue(be.machineRecipes().size() == 1, "one machine recipe in the ROM");
        helper.assertFalse(be.loadMachineRecipe(recipe), "a duplicate is rejected by dedupe");
        helper.assertTrue(be.romUsed() == 1, "the machine recipe counts toward ROM use");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void multiStagePattern_roundTrips(final GameTestHelper helper) {
        final var registries = helper.getLevel().registryAccess();
        final ProcessingPattern proc = new ProcessingPattern(
                List.of(new ProcessingInput(StorageKey.of(Items.IRON_INGOT), 1L)),
                List.of(new ProcessingOutput(StorageKey.of(Items.COPPER_INGOT), 1L, 100)),
                "Furnace", 150);
        final MultiStagePattern multi = new MultiStagePattern(List.of(MultiStagePattern.Stage.proc(proc)));

        final String snbt = CraftFile.serializeMultiStage(multi, registries).orElseThrow();
        helper.assertTrue("multi".equals(CraftFile.typeOf(snbt)), "the tagged type is multi");
        final MultiStagePattern parsed = CraftFile.parseMultiStage(snbt, registries).orElseThrow();
        helper.assertTrue(parsed.stages().size() == 1, "one stage survives");
        helper.assertTrue(parsed.stages().get(0).isProcessing(), "the stage is a processing stage");
        helper.succeed();
    }
}
