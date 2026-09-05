/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jsc.jscomputronics.module.computing.crafting.AnyTagResolver;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.os.fs.CraftFile;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The second shape of a recipe file: a name and a note from its author, and cells that accept a tag instead
 * of one exact item. Old files keep reading as they did, and a craft resolves "any planks" to whatever
 * planks the network holds most of.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CraftPatternV2GameTests {

    private CraftPatternV2GameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final String PLANKS = "minecraft:planks";

    /** A chest: eight planks around an empty middle, the middle-row planks accepting any planks. */
    private static CraftingPattern chestOfAnyPlanks() {
        final List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                grid.set(i, new ItemStack(Items.OAK_PLANKS));
            }
        }
        CraftingPattern pattern = new CraftingPattern(grid, new ItemStack(Items.CHEST))
                .withName("Chest, any planks", "keeps the middle empty");
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                pattern = pattern.withAnyTag(i, PLANKS);
            }
        }
        return pattern;
    }

    @GameTest(template = ARENA)
    public static void craftFile_carriesNameNoteAndAnyTagsThroughAFile(final GameTestHelper helper) {
        final var registries = helper.getLevel().registryAccess();
        final CraftingPattern pattern = chestOfAnyPlanks();
        final String content = CraftFile.serialize(pattern, registries).orElseThrow();
        final CraftingPattern back = CraftFile.parse(content, registries).orElseThrow();
        helper.assertTrue(back.name().equals("Chest, any planks") && back.note().equals("keeps the middle empty"),
                "the name and note come back; got " + back.name() + " / " + back.note());
        helper.assertTrue(back.anyTagAt(0).equals(PLANKS) && back.anyTagAt(4).isEmpty(),
                "the tags come back per cell; got " + back.anyTags());
        helper.assertTrue(back.equals(pattern) && back.sameRecipe(pattern), "the recipe is the same recipe");
        helper.assertTrue(back.displayName().equals("Chest, any planks"), "a named pattern goes by its name");

        final ProcessingPattern machine = new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.IRON_INGOT), 1L)),
                List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(Items.IRON_NUGGET), 9L, 100)),
                "minecraft:furnace", 200).withName("Nuggets", "");
        final ProcessingPattern machineBack = CraftFile.parseProcessing(
                CraftFile.serializeProcessing(machine, registries).orElseThrow(), registries).orElseThrow();
        helper.assertTrue(machineBack.name().equals("Nuggets") && machineBack.sameRecipe(machine),
                "a machine recipe keeps its name through a file");
        final MultiStagePattern pipeline = new MultiStagePattern(
                List.of(MultiStagePattern.Stage.bench(pattern), MultiStagePattern.Stage.proc(machine)))
                .withName("Chest line", "two stages");
        final MultiStagePattern pipelineBack = CraftFile.parseMultiStage(
                CraftFile.serializeMultiStage(pipeline, registries).orElseThrow(), registries).orElseThrow();
        helper.assertTrue(pipelineBack.name().equals("Chest line") && pipelineBack.stages().size() == 2,
                "a pipeline keeps its name through a file");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftFile_aFileFromBeforeNamesAndTagsStillReads(final GameTestHelper helper) {
        final var registries = helper.getLevel().registryAccess();
        final List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.OAK_LOG));
        final CraftingPattern plain = new CraftingPattern(grid, new ItemStack(Items.OAK_PLANKS, 4));
        // A plain pattern writes exactly what the old format wrote: no name, note or tag fields at all.
        final String content = CraftFile.serialize(plain, registries).orElseThrow();
        helper.assertTrue(!content.contains("name") && !content.contains("any"),
                "an unnamed, exact pattern writes the old shape; got " + content);
        final Optional<CraftingPattern> back = CraftFile.parse(content, registries);
        helper.assertTrue(back.isPresent() && back.get().name().isEmpty() && !back.get().hasAnyTags(),
                "the old shape reads back as an exact, unnamed pattern");
        helper.assertTrue(back.get().displayName().equals(plain.result().getHoverName().getString()),
                "an unnamed pattern goes by its result");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void anyTagResolver_takesTheStockedItemTheNetworkHasMostOf(final GameTestHelper helper) {
        final Map<StorageKey, Long> stock = Map.of(
                StorageKey.of(Items.OAK_PLANKS), 3L,
                StorageKey.of(Items.BIRCH_PLANKS), 64L,
                StorageKey.of(Items.COBBLESTONE), 500L);
        final CraftingPattern resolved = AnyTagResolver.resolve(chestOfAnyPlanks(), stock);
        helper.assertTrue(resolved.grid().get(0).is(Items.BIRCH_PLANKS) && resolved.grid().get(8).is(Items.BIRCH_PLANKS),
                "any planks resolves to the birch planks the network has most of; got " + resolved.grid().get(0));
        helper.assertTrue(resolved.hasAnyTags() && resolved.name().equals("Chest, any planks"),
                "the resolved pattern still says what it accepts and what it is called");
        final CraftingPattern untouched = AnyTagResolver.resolve(chestOfAnyPlanks(),
                Map.of(StorageKey.of(Items.COBBLESTONE), 500L));
        helper.assertTrue(untouched.grid().get(0).is(Items.OAK_PLANKS),
                "with nothing of the tag in stock the cell keeps what the author drew");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craft_usesWhateverPlanksTheNetworkHolds(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(Items.BIRCH_PLANKS, 8);
                    helper.assertTrue(net.cc().loadPattern(chestOfAnyPlanks()), "the pattern goes into the ROM");
                })
                .thenExecuteAfter(SETTLE + 2, () -> helper.assertTrue(
                        net.mainframe().submitNetworkCraft(StorageKey.of(Items.CHEST), 1L, false, "test") != null,
                        "a chest is craftable from birch planks although the pattern was drawn with oak"))
                .thenExecuteAfter(60, () -> {
                    final var storage = net.storage(helper.getLevel());
                    helper.assertTrue(storage.count(Items.CHEST) == 1,
                            "the chest was crafted; got " + storage.count(Items.CHEST));
                    helper.assertTrue(storage.count(Items.BIRCH_PLANKS) == 0,
                            "the birch planks were what got used; " + storage.count(Items.BIRCH_PLANKS) + " left");
                })
                .thenSucceed();
    }
}
