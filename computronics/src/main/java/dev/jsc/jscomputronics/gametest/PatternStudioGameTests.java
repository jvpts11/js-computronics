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
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.PatternWorkbench;
import dev.jsc.jscomputronics.module.computing.crafting.RecipeBook;
import dev.jsc.jscomputronics.module.computing.crafting.RecipeMachines;
import dev.jsc.jscomputronics.module.computing.operation.payload.PatternStudioPayloads;
import dev.jsc.jscomputronics.module.computing.operation.payload.PatternStudioStatePayload;
import dev.jsc.jscomputronics.module.computing.os.fs.CraftFile;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.CraftFiles;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The Pattern Studio's workbench on a computer: its drafts survive the machine's save, the Recipe Book lays a
 * recipe out with the tags its ingredients stand for and pairs a machine recipe with a machine the data maps,
 * the state a window receives says what the network holds behind each cell and which drives hold files, and
 * a finished draft goes to the encoder, the disk and the Recipe ROM.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class PatternStudioGameTests {

    private PatternStudioGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final BlockPos ENCODER = new BlockPos(5, 2, 1);
    private static final BlockPos DVD_DRIVE = new BlockPos(5, 2, 3);

    @GameTest(template = ARENA)
    public static void workbench_draftsSurviveTheComputersSave(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final CraftingComputerBlockEntity cc = world.placeRunningCraftingComputer(new BlockPos(2, 2, 2));
        final PatternWorkbench studio = cc.studio();
        studio.setGhost(0, new ItemStack(Items.OAK_LOG));
        studio.setAnyTag(0, "minecraft:logs");
        studio.setBenchName("Planks of any log", "the log the network has most of");
        studio.setProcCell(false, 2, new PatternWorkbench.DataCell(StorageKey.of(Items.RAW_IRON), 3, false));
        studio.setProcCell(true, 0, new PatternWorkbench.DataCell(StorageKey.of(Items.IRON_INGOT), 1, false));
        studio.setOutputChance(0, 50);
        studio.setMachineType("minecraft:furnace");
        studio.setProcTimeout(600);
        studio.setProcName("Smelt", "");
        studio.addStage(dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern.Stage.proc(
                CraftFiles.furnaceIron(200)));
        studio.setPipelineName("Iron line", "");
        studio.remember(PatternWorkbench.Kind.MACHINE, "disk", "smelt.craft");

        final CompoundTag saved = new CompoundTag();
        studio.save(saved, reg);
        final PatternWorkbench back = new PatternWorkbench();
        back.load(saved, reg);
        helper.assertTrue(back.grid().get(0).is(Items.OAK_LOG) && "minecraft:logs".equals(back.anyTags().get(0)),
                "the bench cell and its tag survive");
        helper.assertTrue("Planks of any log".equals(back.benchName()), "the bench name survives");
        helper.assertTrue(back.procInput(2) != null && back.procInput(2).amount() == 3
                && back.procOutput(0) != null && back.outputChance(0) == 50, "the machine cells survive");
        helper.assertTrue("minecraft:furnace".equals(back.machineType()) && back.procTimeout() == 600
                && "Smelt".equals(back.procName()), "the machine header survives");
        helper.assertTrue(back.stages().size() == 1 && "Iron line".equals(back.pipelineName()), "the pipeline survives");
        helper.assertTrue("smelt.craft".equals(back.openedFile(PatternWorkbench.Kind.MACHINE))
                && "disk".equals(back.openedSource(PatternWorkbench.Kind.MACHINE)), "the provenance survives");

        // The computer itself carries it: its own save holds the drafts under its Studio tag.
        final CompoundTag ccTag = cc.saveWithFullMetadata(reg);
        helper.assertTrue(ccTag.contains("Studio") && ccTag.getCompound("Studio").contains("Grid"),
                "the computer saves its workbench");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void recipeLayout_benchGridTagsAndMachineMapping(final GameTestHelper helper) {
        final var level = helper.getLevel();
        // A chest: eight planks around an empty middle, and every planks ingredient stands for the planks tag.
        final var chest = level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("chest")).orElseThrow();
        final List<ItemStack> grid = RecipeBook.benchGrid(chest.value());
        helper.assertTrue(grid.size() == 9 && grid.get(4).isEmpty() && !grid.get(0).isEmpty(),
                "the chest lays out on the 3x3 grid with an empty middle");
        final List<String> tags = RecipeBook.benchTags(chest.value());
        helper.assertTrue("minecraft:planks".equals(tags.get(0)) && tags.get(4).isEmpty(),
                "a planks ingredient stands for the planks tag; got " + tags);
        // A shapeless recipe fills the grid in reading order, and a single-item ingredient carries no tag.
        final var nugget = level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("iron_nugget")).orElseThrow();
        final List<ItemStack> nuggetGrid = RecipeBook.benchGrid(nugget.value());
        helper.assertTrue(nuggetGrid.get(0).is(Items.IRON_INGOT) && nuggetGrid.get(1).isEmpty(),
                "the nugget recipe puts its one ingot in the first cell");
        helper.assertTrue(RecipeBook.benchTags(nugget.value()).get(0).isEmpty(), "an exact ingredient has no tag");
        // A recipe type maps to the machine family the data names; the smelting family is the furnaces.
        RecipeMachines.replace(Map.of("minecraft:smelting", List.of("minecraft:furnace", "minecraft:blast_furnace")));
        helper.assertTrue(RecipeMachines.machinesFor("minecraft:smelting")
                        .equals(List.of("minecraft:furnace", "minecraft:blast_furnace")),
                "the data maps smelting to the furnaces; got " + RecipeMachines.machinesFor("minecraft:smelting"));
        helper.assertTrue(RecipeMachines.machinesFor("minecraft:nothing").isEmpty(), "an unmapped type has no machines");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void studioState_showsStockDrivesEncoderAndRom(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
        world.setBlock(DVD_DRIVE, ComputingModule.DVD_DRIVE.get());
        final PatternEncoderBlockEntity encoder = world.blockEntity(ENCODER, PatternEncoderBlockEntity.class);
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.DVD_RW.get()));
        final MediaReaderBlockEntity drive = world.blockEntity(DVD_DRIVE, MediaReaderBlockEntity.class);
        final ItemStack disc = new ItemStack(ComputingModule.DVD_RW.get());
        CraftFiles.writeBench(disc, CraftFiles.oakPlanks(), helper.getLevel().registryAccess());
        drive.mediaSlot().setStackInSlot(0, disc);
        net.seed(Items.BIRCH_PLANKS, 20);
        net.seed(Items.OAK_PLANKS, 5);

        final PatternWorkbench studio = net.cc().studio();
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                studio.setGhost(i, new ItemStack(Items.OAK_PLANKS));
                studio.setAnyTag(i, "minecraft:planks");
            }
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    studio.refreshPreview(helper.getLevel());
                    helper.assertTrue(studio.preview().is(Items.CHEST), "eight planks preview a chest");
                    final PatternStudioStatePayload state =
                            PatternStudioPayloads.buildState(helper.getLevel(), net.cc(), "", -1);
                    final PatternStudioStatePayload.BenchCell cell = state.bench().get(0);
                    helper.assertTrue(cell.resolved().is(Items.BIRCH_PLANKS) && cell.stock() == 20L,
                            "an any-planks cell resolves to the planks the network has most of; got "
                                    + cell.resolved() + " x" + cell.stock());
                    helper.assertTrue(state.encoder().linked() && "Standard".equals(state.encoder().era())
                            && !state.encoder().media().isEmpty(), "the linked encoder is reported; got " + state.encoder());
                    boolean discListed = false;
                    for (final PatternStudioStatePayload.Drive d : state.drives()) {
                        if (d.key().startsWith("media:") && d.files().contains("oak_planks.craft")) {
                            discListed = true;
                        }
                    }
                    helper.assertTrue(discListed, "the drive's disc and its file are listed; got " + state.drives());
                    helper.assertTrue(state.craftingComputer() && state.hasCard() && !state.romHasBench(),
                            "a Crafting Computer with a card, the draft not yet in the ROM");
                    helper.assertTrue(net.cc().loadPattern(studio.benchPattern()), "the draft loads into the ROM");
                    final PatternStudioStatePayload after =
                            PatternStudioPayloads.buildState(helper.getLevel(), net.cc(), "", -1);
                    helper.assertTrue(after.romHasBench(), "the state says the draft is in the ROM now");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void studio_opensFilesAndBurnsDrafts(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
        world.setBlock(DVD_DRIVE, ComputingModule.DVD_DRIVE.get());
        final PatternEncoderBlockEntity encoder = world.blockEntity(ENCODER, PatternEncoderBlockEntity.class);
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.DVD_RW.get()));
        final MediaReaderBlockEntity drive = world.blockEntity(DVD_DRIVE, MediaReaderBlockEntity.class);
        final ItemStack disc = new ItemStack(ComputingModule.DVD_RW.get());
        CraftFiles.writeProcessing(disc, CraftFiles.furnaceIron(300), reg);
        drive.mediaSlot().setStackInSlot(0, disc);
        final PatternWorkbench studio = net.cc().studio();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // Open the machine file from the drive: it lands in the machine draft with its provenance.
                    final String key = "media:" + helper.absolutePos(DVD_DRIVE).asLong();
                    final String content = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem
                            .read(disc, "iron_ingot.craft").orElseThrow();
                    helper.assertTrue("proc".equals(CraftFile.typeOf(content)), "the file is a machine recipe");
                    studio.loadMachine(CraftFile.parseProcessing(content, reg).orElseThrow(), key, "iron_ingot.craft");
                    helper.assertTrue(studio.machineComplete() && studio.procTimeout() == 300
                            && "iron_ingot.craft".equals(studio.openedFile(PatternWorkbench.Kind.MACHINE)),
                            "the opened machine recipe fills the draft");
                    // Add it as a pipeline stage, then a bench stage from the bench draft, and burn the pipeline.
                    helper.assertTrue(studio.addProcessingStage(), "the machine draft becomes a stage");
                    helper.assertFalse(studio.machineComplete(), "adding a stage clears the machine cells");
                    studio.setGhost(0, new ItemStack(Items.IRON_INGOT));
                    studio.refreshPreview(helper.getLevel());
                    helper.assertTrue(studio.preview().is(Items.IRON_NUGGET), "an ingot previews nuggets");
                    helper.assertTrue(studio.addBenchStage(), "the bench draft becomes a stage");
                    helper.assertTrue(studio.stages().size() == 2 && studio.stages().get(0).isProcessing(),
                            "two stages in order");
                    studio.setPipelineName("Nuggets from ore", "");
                    final var content2 = studio.serialize(PatternWorkbench.Kind.PIPELINE, reg);
                    helper.assertTrue(content2.isPresent() && encoder.queueBurn("nuggets_from_ore", content2.get()),
                            "the pipeline goes to the encoder");
                })
                .thenExecuteAfter(150, () -> {
                    helper.assertTrue(encoder.completed() == 1, "the pipeline burned; completed=" + encoder.completed());
                    final String back = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem
                            .read(encoder.mediaStack(), "nuggets_from_ore.craft").orElse("");
                    final var parsed = CraftFile.parseMultiStage(back, reg);
                    helper.assertTrue(parsed.isPresent() && parsed.get().stages().size() == 2
                            && "Nuggets from ore".equals(parsed.get().name()), "the burned pipeline reads back with its name");
                    // The same draft loads straight into this computer's ROM as a machine recipe.
                    helper.assertTrue(net.cc().loadMachineRecipe(
                            dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe.ofMultiStage(studio.multiStagePattern())),
                            "the pipeline loads into the ROM");
                    final CraftingPattern bench = CraftFiles.oakPlanks();
                    helper.assertTrue(net.cc().loadPattern(bench), "a bench pattern loads beside it");
                })
                .thenSucceed();
    }
}
