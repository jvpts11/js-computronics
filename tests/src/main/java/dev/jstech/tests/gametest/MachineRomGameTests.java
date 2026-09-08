/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity.MachineConfig;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.CraftingSwitchBlockEntity;
import dev.jstech.computers.crafting.CraftingPattern;
import dev.jstech.computers.crafting.NetworkRecipe;
import dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput;
import dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput;
import dev.jstech.computers.crafting.ProcessingPattern;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.industrial.IndustrialModule;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Battery 1, front B: the Recipe ROM (machine + bench recipes share one budget), the machine index a switch
 * declares, the per-machine concurrency config, and that all of it survives a reload. Adversarial on dedupe,
 * the shared limit, chance/clamp, and active-face filtering.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MachineRomGameTests {

    private MachineRomGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void rom_machineRecipeDedupeAndSharedLimit(final GameTestHelper helper) {
        final CraftingComputerBlockEntity cc = placeComputer(helper, new BlockPos(2, 2, 2));
        final NetworkRecipe recipe = proc("jsindustrial:macerator");
        helper.assertTrue(cc.loadMachineRecipe(recipe), "first load accepted");
        helper.assertFalse(cc.loadMachineRecipe(recipe), "an identical recipe is deduped");
        helper.assertTrue(cc.machineRecipes().size() == 1, "still one recipe after the duplicate");

        // Fill the shared ROM budget to the limit, then prove one more (machine or bench) is rejected.
        int loaded = 1;
        for (int i = 0; loaded < CraftingComputerBlockEntity.RECIPE_ROM_LIMIT; i++) {
            if (cc.loadMachineRecipe(proc("machine_" + i))) {
                loaded++;
            }
        }
        helper.assertTrue(cc.romUsed() == CraftingComputerBlockEntity.RECIPE_ROM_LIMIT, "ROM filled to the limit");
        helper.assertFalse(cc.loadMachineRecipe(proc("one_too_many")), "a machine recipe over the limit is rejected");
        helper.assertFalse(cc.loadPattern(benchPattern(Items.DIAMOND_BLOCK)),
                "a bench recipe over the SHARED limit is rejected too");
        helper.assertTrue(cc.romUsed() == CraftingComputerBlockEntity.RECIPE_ROM_LIMIT, "the limit held");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rom_removeMachineRecipe(final GameTestHelper helper) {
        final CraftingComputerBlockEntity cc = placeComputer(helper, new BlockPos(2, 2, 2));
        cc.loadMachineRecipe(proc("a"));
        cc.loadMachineRecipe(proc("b"));
        helper.assertTrue(cc.machineRecipes().size() == 2, "two recipes loaded");
        cc.removeMachineRecipe(0);
        helper.assertTrue(cc.machineRecipes().size() == 1, "one recipe after removal");
        cc.removeMachineRecipe(99); // out of range is a safe no-op
        helper.assertTrue(cc.machineRecipes().size() == 1, "out-of-range removal is a no-op");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void machineConfig_clampDefaultAndAccess(final GameTestHelper helper) {
        final CraftingComputerBlockEntity cc = placeComputer(helper, new BlockPos(2, 2, 2));
        helper.assertTrue(cc.machineConfig("unknown").equals(MachineConfig.DEFAULT), "unknown key gives DEFAULT");
        helper.assertTrue(MachineConfig.DEFAULT.maxJobs() == 0, "DEFAULT is auto (0 = use every machine)");
        helper.assertTrue(new MachineConfig(0, false, false).maxJobs() == 0, "maxJobs 0 stays 0 (auto)");
        helper.assertTrue(new MachineConfig(-9, false, false).maxJobs() == 0, "negative maxJobs clamps to 0 (auto)");
        cc.setMachineConfig("jsindustrial:compressor", new MachineConfig(4, true, true));
        helper.assertTrue(cc.machineConfig("jsindustrial:compressor").maxJobs() == 4, "set config is read back");
        helper.assertTrue(cc.machineConfig("jsindustrial:compressor").locked(), "locked is read back");
        cc.setMachineConfig("", new MachineConfig(8, false, false)); // blank key is ignored
        helper.assertTrue(cc.machineConfig("").equals(MachineConfig.DEFAULT), "a blank key is not stored");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void persistence_romAndConfigSurviveReload(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final BlockPos pos = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity cc = placeComputer(helper, pos);
        cc.loadPattern(benchPattern(Items.IRON_BLOCK));
        cc.loadMachineRecipe(proc("jsindustrial:macerator"));
        cc.setMachineConfig("jsindustrial:macerator", new MachineConfig(6, true, true));

        final CompoundTag saved = cc.saveWithFullMetadata(reg);
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof CraftingComputerBlockEntity reloaded)) {
            helper.fail("no reloaded computer");
            return;
        }
        reloaded.loadWithComponents(saved, reg);
        helper.assertTrue(reloaded.romPatterns().size() == 1, "bench ROM survived the reload");
        helper.assertTrue(reloaded.machineRecipes().size() == 1, "machine ROM survived the reload");
        final MachineConfig cfg = reloaded.machineConfig("jsindustrial:macerator");
        helper.assertTrue(cfg.maxJobs() == 6 && cfg.locked() && cfg.feedMax(), "the machine config survived");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void index_declaredMachinesRespectActiveFaces(final GameTestHelper helper) {
        final BlockPos swPos = new BlockPos(2, 2, 2);
        final BlockPos machinePos = swPos.relative(Direction.NORTH);
        helper.setBlock(swPos, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machinePos, IndustrialModule.COMPRESSOR.get());
        helper.startSequence()
                .thenExecuteAfter(4, () -> {
                    if (!(helper.getBlockEntity(swPos) instanceof CraftingSwitchBlockEntity sw)) {
                        helper.fail("no switch");
                        return;
                    }
                    helper.assertTrue(sw.declaredMachines().size() == 1,
                            "an active face touching a machine is declared; got " + sw.declaredMachines().size());
                    sw.setFaceActive(Direction.NORTH, false);
                    helper.assertTrue(sw.declaredMachines().isEmpty(), "an inactive face declares nothing");
                    sw.setFaceActive(Direction.NORTH, true);
                    helper.assertTrue(sw.declaredMachines().size() == 1, "re-activating restores the declaration");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void migration_loadWithoutMachineDataUsesDefaults(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final BlockPos pos = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity cc = placeComputer(helper, pos);
        cc.loadPattern(benchPattern(Items.IRON_BLOCK)); // bench only, like a pre-Slice-C save
        final CompoundTag saved = cc.saveWithFullMetadata(reg);
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof CraftingComputerBlockEntity reloaded)) {
            helper.fail("no reloaded computer");
            return;
        }
        reloaded.loadWithComponents(saved, reg);
        helper.assertTrue(reloaded.romPatterns().size() == 1, "the bench ROM still loads");
        helper.assertTrue(reloaded.machineRecipes().isEmpty(), "absent machine ROM loads as empty, no crash");
        helper.assertTrue(reloaded.machineConfig("jsc:x").equals(MachineConfig.DEFAULT),
                "absent machine config defaults");
        helper.succeed();
    }

    // builders

    private static CraftingComputerBlockEntity placeComputer(final GameTestHelper helper, final BlockPos pos) {
        helper.setBlock(pos, ComputingModule.CRAFTING_COMPUTER.get());
        if (helper.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc) {
            return cc;
        }
        helper.fail("no Crafting Computer at " + pos);
        throw new IllegalStateException("unreachable");
    }

    private static NetworkRecipe proc(final String machineType) {
        return NetworkRecipe.ofProcessing(new ProcessingPattern(
                List.of(new ProcessingInput(StorageKey.of(Items.IRON_INGOT), 1L)),
                List.of(new ProcessingOutput(StorageKey.of(Items.COPPER_INGOT), 1L, 100)),
                machineType, 200));
    }

    private static CraftingPattern benchPattern(final net.minecraft.world.item.Item result) {
        final List<ItemStack> cells = new ArrayList<>();
        cells.add(new ItemStack(Items.IRON_INGOT));
        for (int i = 1; i < CraftingPattern.GRID_SIZE; i++) {
            cells.add(ItemStack.EMPTY);
        }
        return new CraftingPattern(cells, new ItemStack(result));
    }
}
