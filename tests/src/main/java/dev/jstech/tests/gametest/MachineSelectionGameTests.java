/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.block.part.InputBusPart;
import dev.jstech.computronics.block.part.ReceivingBusPart;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.crafting.NetworkRecipe;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Machine selection must honor the bus filters, not just the block type. Two machines of the same type are told
 * apart only by their Crafting Input Bus filters — one factory takes iron, another takes enriched iron — so a
 * recipe whose input only the second machine's bus can carry must run on that second machine, not fail on the
 * first. An unfiltered Input Bus is a wildcard, so a machine carrying it can run any recipe of its type. This is
 * the multi-machine group setup a player builds to spread load without congesting one machine.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MachineSelectionGameTests {

    private MachineSelectionGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final String FURNACE = "minecraft:furnace";

    private static ProcessingPattern smelt(final net.minecraft.world.item.Item in, final net.minecraft.world.item.Item out) {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(in), 1)),
                List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(out), 1, 100)),
                FURNACE, 200);
    }

    /** Mounts an Input Bus (filtered to {@code filter}) above and a Receiving Bus below the furnace. */
    private static void wireFurnace(final GameTestHelper helper, final BlockPos furnace,
                                    final net.minecraft.world.item.Item filter) {
        final BlockPos above = furnace.above();
        final BlockPos below = furnace.below();
        helper.setBlock(above, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(below, ComputingModule.CRAFTING_CABLE.get());
        if (helper.getBlockEntity(above) instanceof DataCableBlockEntity cable) {
            final InputBusPart in = new InputBusPart();
            cable.addPart(Direction.DOWN, in);
            in.setFilter(new ItemStack(filter));
        }
        if (helper.getBlockEntity(below) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.UP, new ReceivingBusPart());
        }
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void selectsTheMachineWhoseBusFilterRoutesTheInput(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos furnaceA = new BlockPos(5, 2, 5); // south face of the switch
        final BlockPos furnaceB = new BlockPos(6, 2, 4); // east face of the switch
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(furnaceA, Blocks.FURNACE);
        helper.setBlock(furnaceB, Blocks.FURNACE);
        final StorageKey copperIngot = StorageKey.of(Items.COPPER_INGOT);

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // Furnace A only carries raw iron; furnace B only carries raw copper.
                    wireFurnace(helper, furnaceA, Items.RAW_IRON);
                    wireFurnace(helper, furnaceB, Items.RAW_COPPER);
                    // Finished copper ingots sit in furnace B's output so the receiving path has something to
                    // pull without waiting out a real smelt; furnace A holds none.
                    if (helper.getBlockEntity(furnaceB) instanceof FurnaceBlockEntity furnace) {
                        furnace.setItem(2, new ItemStack(Items.COPPER_INGOT, 8));
                    }
                    net.seed(Items.RAW_COPPER, 16);
                    // One recipe: raw copper -> copper ingot in a furnace. Only furnace B's bus carries raw copper.
                    helper.assertTrue(net.cc().loadMachineRecipe(NetworkRecipe.ofProcessing(smelt(Items.RAW_COPPER, Items.COPPER_INGOT))),
                            "the copper recipe must load");
                })
                .thenExecuteAfter(SETTLE + 2, () ->
                        helper.assertTrue(net.mainframe().submitNetworkProcessing(smelt(Items.RAW_COPPER, Items.COPPER_INGOT), 4, "sel") != null,
                                "the processing operation is accepted"))
                .thenWaitUntil(() -> {
                    // Only reachable if the engine chose furnace B (whose bus routes raw copper) and collected its
                    // output; choosing furnace A would block the input on its iron filter and collect nothing.
                    final long have = net.storage(helper.getLevel()).count(copperIngot);
                    if (have < 4) {
                        throw new GameTestAssertException("engine must route to the copper-capable furnace; network copper="
                                + have + " (choosing the iron furnace would leave 0)");
                    }
                })
                .thenSucceed();
    }
}
