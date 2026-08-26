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
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Battery 1, front E (Pattern Encoder authoring): write-gating, the ghost-grid build, stage assembly, and that
 * the authoring state survives a reload. Exercised on the block entity directly (the screen sends edits to these
 * same methods through a server-authoritative payload).
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class PatternEncoderGameTests {

    private PatternEncoderGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void patternEncoder_writeGatingAndBuild(final GameTestHelper helper) {
        final PatternEncoderBlockEntity be = place(helper, new BlockPos(2, 2, 2));
        helper.assertFalse(be.canWriteProcessing(), "an empty encoder cannot write a processing pattern");
        helper.assertFalse(be.canWriteMultiStage(), "an empty encoder cannot write a multi-stage pattern");

        be.setMachineType("jsc:compressor");
        be.setProcInput(0, new ItemStack(Items.IRON_INGOT, 4));
        helper.assertFalse(be.canWriteProcessing(), "inputs without outputs cannot write");
        be.setProcOutput(0, new ItemStack(Items.IRON_BLOCK, 1));

        final ProcessingPattern built = be.buildProcessingPattern();
        helper.assertTrue(built.inputs().size() == 1 && built.inputs().get(0).amount() == 4,
                "the input amount is the placed stack count");
        helper.assertTrue(built.outputs().size() == 1, "one output is recorded");
        helper.assertTrue("jsc:compressor".equals(built.machineType()), "the machine type is recorded");
        // Even fully authored, no writable media is inserted, so the write stays gated.
        helper.assertFalse(be.canWriteProcessing(), "no media still blocks the write");

        be.setMachineType("");
        be.setProcInput(0, new ItemStack(Items.IRON_INGOT, 4));
        be.setProcOutput(0, new ItemStack(Items.IRON_BLOCK, 1));
        helper.assertFalse(be.canWriteProcessing(), "a blank machine blocks the write");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void patternEncoder_processingStageAssembly(final GameTestHelper helper) {
        final PatternEncoderBlockEntity be = place(helper, new BlockPos(2, 2, 2));
        helper.assertFalse(be.addProcessingStage(), "no machine/inputs/outputs means no stage is added");

        be.setMachineType("jsc:macerator");
        be.setProcInput(0, new ItemStack(Items.IRON_ORE, 1));
        be.setProcOutput(0, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(be.addProcessingStage(), "a complete processing stage is added");
        helper.assertTrue(be.stages().size() == 1, "one stage assembled");
        helper.assertTrue(be.stages().get(0).isProcessing(), "it is a processing stage");

        be.removeStage(5); // out of range is a safe no-op
        helper.assertTrue(be.stages().size() == 1, "out-of-range removal is a no-op");
        be.removeStage(0);
        helper.assertTrue(be.stages().isEmpty(), "the stage is removed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void patternEncoder_authoringStateSurvivesReload(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final BlockPos pos = new BlockPos(2, 2, 2);
        final PatternEncoderBlockEntity be = place(helper, pos);
        be.setMachineType("jsc:macerator");
        be.setProcInput(0, new ItemStack(Items.IRON_INGOT, 3));
        be.setProcOutput(1, new ItemStack(Items.GOLD_NUGGET, 5));
        be.setOutputChance(1, 50);
        be.setProcTimeout(123);

        final CompoundTag saved = be.saveWithFullMetadata(reg);
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ComputingModule.PATTERN_ENCODER.get());
        if (!(helper.getBlockEntity(pos) instanceof PatternEncoderBlockEntity reloaded)) {
            helper.fail("no reloaded encoder");
            return;
        }
        reloaded.loadWithComponents(saved, reg);
        helper.assertTrue("jsc:macerator".equals(reloaded.machineType()), "machine type survived");
        helper.assertTrue(reloaded.procInputs().getStackInSlot(0).getCount() == 3, "input survived");
        helper.assertTrue(reloaded.procOutputs().getStackInSlot(1).getCount() == 5, "output survived");
        helper.assertTrue(reloaded.outputChance(1) == 50, "output chance survived");
        helper.assertTrue(reloaded.procTimeout() == 123, "timeout survived");
        helper.succeed();
    }

    private static PatternEncoderBlockEntity place(final GameTestHelper helper, final BlockPos pos) {
        helper.setBlock(pos, ComputingModule.PATTERN_ENCODER.get());
        if (helper.getBlockEntity(pos) instanceof PatternEncoderBlockEntity be) {
            return be;
        }
        helper.fail("no Pattern Encoder at " + pos);
        throw new IllegalStateException("unreachable");
    }
}
