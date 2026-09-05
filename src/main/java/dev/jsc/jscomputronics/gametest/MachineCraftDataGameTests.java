/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern.ProcessingInput;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern.ProcessingOutput;
import dev.jsc.jscomputronics.module.computing.os.fs.CraftFile;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Battery 1, front A: the machine-crafting DATA model and {@code .craft} serialization, exhaustively and
 * adversarially — chance clamping and yield math, ingredient totals, recipe equality, item AND fluid round-trips,
 * preserved chances, mixed multi-stage round-trips, and malformed/unknown content. Pure data behavior validated
 * with the registries a running server provides (StorageKey/ItemStack/FluidStack are MC types, so this is a
 * GameTest, not JUnit).
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class MachineCraftDataGameTests {

    private MachineCraftDataGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void processingOutput_clampsChanceToValidRange(final GameTestHelper helper) {
        helper.assertTrue(out(Items.IRON_INGOT, 1, 0).chancePercent() == 1, "0% clamps up to 1");
        helper.assertTrue(out(Items.IRON_INGOT, 1, -50).chancePercent() == 1, "negative clamps up to 1");
        helper.assertTrue(out(Items.IRON_INGOT, 1, 101).chancePercent() == 100, "over 100 clamps to 100");
        helper.assertTrue(out(Items.IRON_INGOT, 1, Integer.MAX_VALUE).chancePercent() == 100, "MAX clamps to 100");
        helper.assertTrue(out(Items.IRON_INGOT, 1, 50).chancePercent() == 50, "an in-range chance is kept");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void processingOutput_probabilisticAndExpectedYield(final GameTestHelper helper) {
        helper.assertFalse(out(Items.IRON_INGOT, 4, 100).probabilistic(), "100% is not probabilistic");
        helper.assertTrue(out(Items.IRON_INGOT, 4, 99).probabilistic(), "99% is probabilistic");
        helper.assertTrue(Math.abs(out(Items.IRON_INGOT, 4, 50).expectedYield() - 2.0) < 1e-9,
                "4 @ 50% expects 2.0");
        helper.assertTrue(Math.abs(out(Items.IRON_INGOT, 3, 100).expectedYield() - 3.0) < 1e-9,
                "3 @ 100% expects 3.0");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void processingPattern_normalizesTimeoutAndMachine(final GameTestHelper helper) {
        helper.assertTrue(new ProcessingPattern(List.of(in(Items.IRON_INGOT, 1)), List.of(out(Items.COPPER_INGOT, 1, 100)),
                "m", 0).timeoutTicks() == 1, "timeout 0 normalizes to 1");
        helper.assertTrue(new ProcessingPattern(List.of(in(Items.IRON_INGOT, 1)), List.of(out(Items.COPPER_INGOT, 1, 100)),
                "m", -99).timeoutTicks() == 1, "negative timeout normalizes to 1");
        helper.assertTrue(new ProcessingPattern(List.of(in(Items.IRON_INGOT, 1)), List.of(out(Items.COPPER_INGOT, 1, 100)),
                null, 200).machineType().isEmpty(), "null machine normalizes to empty string");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void processingPattern_ingredientTotalsMergeDuplicates(final GameTestHelper helper) {
        final ProcessingPattern p = new ProcessingPattern(
                List.of(in(Items.IRON_INGOT, 3), in(Items.IRON_INGOT, 5), in(Items.COAL, 2)),
                List.of(out(Items.IRON_BLOCK, 1, 100)), "jsc:compressor", 200);
        final Map<StorageKey, Long> totals = p.ingredientTotals();
        helper.assertTrue(totals.get(StorageKey.of(Items.IRON_INGOT)) == 8L, "iron totals merge to 8");
        helper.assertTrue(totals.get(StorageKey.of(Items.COAL)) == 2L, "coal totals to 2");
        helper.assertTrue(totals.size() == 2, "two distinct ingredient keys");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void processingPattern_primaryOutputAndSameRecipe(final GameTestHelper helper) {
        final ProcessingPattern base = new ProcessingPattern(List.of(in(Items.IRON_INGOT, 1)),
                List.of(out(Items.COPPER_INGOT, 2, 100), out(Items.GOLD_NUGGET, 1, 50)), "jsc:macerator", 200);
        helper.assertTrue(base.primaryOutput() != null
                && base.primaryOutput().key().equals(StorageKey.of(Items.COPPER_INGOT)), "primary is the first output");
        helper.assertTrue(new ProcessingPattern(List.of(), List.of(), "x", 1).primaryOutput() == null,
                "no outputs means null primary");

        helper.assertTrue(base.sameRecipe(copy(base)), "an identical pattern is the same recipe");
        helper.assertFalse(base.sameRecipe(new ProcessingPattern(base.inputs(), base.outputs(), "OTHER", 200)),
                "a different machine is a different recipe");
        helper.assertFalse(base.sameRecipe(new ProcessingPattern(List.of(in(Items.GOLD_INGOT, 1)), base.outputs(),
                "jsc:macerator", 200)), "a different input is a different recipe");
        helper.assertFalse(base.sameRecipe(new ProcessingPattern(base.inputs(),
                List.of(out(Items.COPPER_INGOT, 2, 100), out(Items.GOLD_NUGGET, 1, 75)), "jsc:macerator", 200)),
                "a different output chance is a different recipe");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftFile_processingRoundTripPreservesItemsFluidsAndChances(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final ProcessingPattern original = new ProcessingPattern(
                List.of(in(Items.IRON_INGOT, 7), fluidIn(Fluids.WATER, 250)),
                List.of(out(Items.COPPER_INGOT, 3, 100), out(Items.GOLD_NUGGET, 1, 1),
                        fluidOut(Fluids.LAVA, 50, 99)),
                "jsc:compressor", 175);
        final String snbt = CraftFile.serializeProcessing(original, reg).orElseThrow();
        helper.assertTrue("proc".equals(CraftFile.typeOf(snbt)), "tagged as proc");
        final ProcessingPattern back = CraftFile.parseProcessing(snbt, reg).orElseThrow();
        helper.assertTrue(back.sameRecipe(original), "round-trip preserves the whole recipe");
        helper.assertTrue(back.inputs().get(1).key().isFluid(), "the fluid input survives as a fluid");
        helper.assertTrue(back.outputs().get(1).chancePercent() == 1, "the 1% chance survives");
        helper.assertTrue(back.outputs().get(2).key().isFluid() && back.outputs().get(2).chancePercent() == 99,
                "the probabilistic fluid output survives");
        helper.assertTrue(back.timeoutTicks() == 175, "the timeout survives");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftFile_multiStageMixedRoundTrip(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final ProcessingPattern proc = new ProcessingPattern(List.of(in(Items.IRON_INGOT, 1)),
                List.of(out(Items.IRON_BLOCK, 1, 100)), "jsc:compressor", 200);
        final CraftingPattern bench = new CraftingPattern(grid(new ItemStack(Items.IRON_INGOT)),
                new ItemStack(Items.IRON_BLOCK));
        final MultiStagePattern original = new MultiStagePattern(
                List.of(MultiStagePattern.Stage.bench(bench), MultiStagePattern.Stage.proc(proc)));
        final String snbt = CraftFile.serializeMultiStage(original, reg).orElseThrow();
        helper.assertTrue("multi".equals(CraftFile.typeOf(snbt)), "tagged as multi");
        final MultiStagePattern back = CraftFile.parseMultiStage(snbt, reg).orElseThrow();
        helper.assertTrue(back.stages().size() == 2, "two stages survive");
        helper.assertFalse(back.stages().get(0).isProcessing(), "stage 1 stays a bench stage");
        helper.assertTrue(back.stages().get(1).isProcessing(), "stage 2 stays a processing stage");
        helper.assertTrue(back.finalStage() != null && back.finalStage().isProcessing(),
                "the final stage is the processing one");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftFile_malformedAndUnknownContent(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        helper.assertTrue(CraftFile.parseProcessing("}{not snbt", reg).isEmpty(), "garbage parses to empty");
        helper.assertTrue(CraftFile.parseMultiStage("", reg).isEmpty(), "empty parses to empty");
        helper.assertTrue("craft".equals(CraftFile.typeOf("anything without a type tag")),
                "untagged content is treated as legacy bench");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void networkRecipe_resultKeyAndSameRecipe(final GameTestHelper helper) {
        final ProcessingPattern itemProc = new ProcessingPattern(List.of(in(Items.IRON_INGOT, 1)),
                List.of(out(Items.COPPER_INGOT, 1, 100)), "jsc:macerator", 200);
        final ProcessingPattern fluidProc = new ProcessingPattern(List.of(in(Items.IRON_INGOT, 1)),
                List.of(fluidOut(Fluids.WATER, 1000, 100)), "jsc:compressor", 200);
        final NetworkRecipe r1 = NetworkRecipe.ofProcessing(itemProc);
        helper.assertTrue(r1.usesMachine(), "a processing recipe uses a machine");
        helper.assertTrue(r1.resultKey().equals(StorageKey.of(Items.COPPER_INGOT)), "item result key");
        helper.assertTrue(NetworkRecipe.ofProcessing(fluidProc).resultKey().isFluid(), "fluid result key");
        helper.assertTrue(r1.sameRecipe(NetworkRecipe.ofProcessing(copy(itemProc))), "equal processing recipes match");
        helper.assertFalse(r1.sameRecipe(NetworkRecipe.ofProcessing(fluidProc)), "different recipes do not match");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void storageKey_itemAndFluidIdentity(final GameTestHelper helper) {
        final StorageKey iron = StorageKey.of(Items.IRON_INGOT);
        final StorageKey water = StorageKey.of(new FluidStack(Fluids.WATER, 1000));
        helper.assertFalse(iron.isFluid(), "an item key is not a fluid");
        helper.assertTrue(water.isFluid(), "a fluid key is a fluid");
        helper.assertTrue(iron.equals(StorageKey.of(Items.IRON_INGOT)), "same item keys are equal");
        helper.assertFalse(iron.equals(water), "item and fluid keys differ");
        helper.assertFalse(iron.equals(StorageKey.of(Items.GOLD_INGOT)), "different item keys differ");
        helper.assertFalse(iron.displayName().getString().isBlank(), "an item key has a display name");
        helper.assertFalse(water.displayName().getString().isBlank(), "a fluid key has a display name");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void externalDataPort_routesItemsFluidsAndToleratesNull(final GameTestHelper helper) {
        final net.neoforged.neoforge.items.ItemStackHandler items =
                new net.neoforged.neoforge.items.ItemStackHandler(2);
        final dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort itemPort =
                new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(items, null);
        helper.assertTrue(itemPort.insert(StorageKey.of(Items.IRON_INGOT), 10, false) == 10,
                "items insert into the item handler");
        helper.assertTrue(itemPort.count(StorageKey.of(Items.IRON_INGOT)) == 10, "count reflects the insert");
        helper.assertTrue(itemPort.insert(StorageKey.of(new FluidStack(Fluids.WATER, 1)), 1000, false) == 0,
                "a fluid into an item-only port inserts nothing");
        helper.assertTrue(itemPort.extract(StorageKey.of(Items.IRON_INGOT), 4, false) == 4, "items extract back");

        final dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort nullPort =
                new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(null, null);
        helper.assertTrue(nullPort.isEmpty(), "a port with no handlers is empty");
        helper.assertTrue(nullPort.insert(StorageKey.of(Items.IRON_INGOT), 5, false) == 0,
                "a null port accepts nothing, without crashing");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftFile_preservesItemComponents(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Excalibur"));
        final ProcessingPattern pattern = new ProcessingPattern(
                List.of(new ProcessingInput(StorageKey.of(named), 1L)),
                List.of(out(Items.IRON_INGOT, 1, 100)), "jsc:x", 200);
        final ProcessingPattern back = CraftFile.parseProcessing(
                CraftFile.serializeProcessing(pattern, reg).orElseThrow(), reg).orElseThrow();
        helper.assertTrue(back.inputs().get(0).key().equals(StorageKey.of(named)),
                "a stack's data components survive the .craft round-trip");
        helper.assertFalse(back.inputs().get(0).key().equals(StorageKey.of(new ItemStack(Items.DIAMOND_SWORD))),
                "and it differs from the plain item");
        helper.succeed();
    }

    // --- builders ---

    private static ProcessingInput in(final net.minecraft.world.item.Item item, final long amount) {
        return new ProcessingInput(StorageKey.of(item), amount);
    }

    private static ProcessingInput fluidIn(final net.minecraft.world.level.material.Fluid fluid, final long amount) {
        return new ProcessingInput(StorageKey.of(new FluidStack(fluid, 1)), amount);
    }

    private static ProcessingOutput out(final net.minecraft.world.item.Item item, final long amount,
                                        final int chance) {
        return new ProcessingOutput(StorageKey.of(item), amount, chance);
    }

    private static ProcessingOutput fluidOut(final net.minecraft.world.level.material.Fluid fluid, final long amount,
                                             final int chance) {
        return new ProcessingOutput(StorageKey.of(new FluidStack(fluid, 1)), amount, chance);
    }

    private static ProcessingPattern copy(final ProcessingPattern p) {
        return new ProcessingPattern(p.inputs(), p.outputs(), p.machineType(), p.timeoutTicks());
    }

    private static List<ItemStack> grid(final ItemStack first) {
        final List<ItemStack> cells = new ArrayList<>();
        cells.add(first);
        for (int i = 1; i < CraftingPattern.GRID_SIZE; i++) {
            cells.add(ItemStack.EMPTY);
        }
        return cells;
    }
}
