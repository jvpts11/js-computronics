/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.block.part.InputBusPart;
import dev.jstech.computronics.block.part.ReceivingBusPart;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Operations in flight must survive the world being saved and reopened: the Mainframe writes them into its
 * NBT and resumes them after the boot. These tests replay that round trip server-side — snapshot the running
 * Mainframe's NBT, replace the block, load the snapshot into the fresh block entity — and check the craft
 * carries on where it stopped instead of vanishing.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OperationResumeGameTests {

    private OperationResumeGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final BlockPos MAINFRAME = new BlockPos(1, 2, 2);
    private static final BlockPos FURNACE = new BlockPos(5, 2, 5);

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void processing_resumesAfterTheMainframeIsSavedAndReloaded(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(new BlockPos(5, 2, 3), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 2, 4), ComputingModule.CRAFTING_SWITCH.get());
        world.setBlock(FURNACE, Blocks.FURNACE);
        world.setBlock(new BlockPos(5, 3, 5), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 1, 5), ComputingModule.CRAFTING_CABLE.get());
        final CompoundTag[] snapshot = new CompoundTag[1];

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (world.getBlockEntity(new BlockPos(5, 3, 5)) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.DOWN, new InputBusPart());
                    }
                    if (world.getBlockEntity(new BlockPos(5, 1, 5)) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.UP, new ReceivingBusPart());
                    }
                    net.seed(Items.RAW_IRON, 32);
                    if (world.getBlockEntity(FURNACE) instanceof FurnaceBlockEntity furnace) {
                        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.RAW_IRON), 1L)),
                            List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(Items.IRON_INGOT), 1L, 100)),
                            "minecraft:furnace", 600);
                    final var op = net.mainframe().submitNetworkProcessing(pattern, 16, "resume");
                    helper.assertTrue(op != null, "the processing operation is accepted");
                    // A level other than the default must come back with the operation after the reload.
                    op.setPriority(dev.jstech.core.operation.OperationPriority.HIGH);
                })
                // Let it collect the pre-loaded ingots and feed the furnace, then take the world's snapshot.
                .thenExecuteAfter(40, () -> {
                    final long stored = net.storage(helper.getLevel()).count(StorageKey.of(Items.IRON_INGOT));
                    helper.assertTrue(stored >= 8, "the first batch of ingots must be collected before the reload; got " + stored);
                    helper.assertTrue(!net.mainframe().activeOperationRecords().isEmpty(),
                            "the operation must still be running before the reload");
                    snapshot[0] = net.mainframe().saveWithoutMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(snapshot[0].contains("ActiveOperations"),
                            "the Mainframe's NBT must carry the in-flight operation");
                    // Replace the block: the old block entity is torn down like a reload would tear it down,
                    // and the fresh one gets the saved NBT, exactly as loading the chunk would give it.
                    world.setBlock(MAINFRAME, Blocks.AIR);
                })
                .thenExecuteAfter(SETTLE, () -> {
                    world.setBlock(MAINFRAME, ComputingModule.MAINFRAME.get());
                    final MainframeBlockEntity fresh = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    fresh.loadWithComponents(snapshot[0], helper.getLevel().registryAccess());
                    // More finished ingots appear in the furnace while the Mainframe boots (the furnace kept smelting).
                    if (world.getBlockEntity(FURNACE) instanceof FurnaceBlockEntity furnace) {
                        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
                    }
                })
                // Boot + resume delay + a few feed/collect cycles.
                .thenExecuteAfter(80, () -> {
                    final MainframeBlockEntity fresh = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    final long stored = net.storage(helper.getLevel()).count(StorageKey.of(Items.IRON_INGOT));
                    helper.assertTrue(stored >= 16,
                            "the resumed operation must collect the rest of the ingots into storage; got " + stored
                                    + " active=" + fresh.activeOperationRecords() + " recent=" + fresh.recentOperations());
                    final boolean completed = fresh.recentOperations().stream()
                            .anyMatch(r -> r.status() == OperationRecord.STATUS_COMPLETED && r.moved() >= 16);
                    helper.assertTrue(completed, "the resumed operation must settle COMPLETED with the full yield; recent="
                            + fresh.recentOperations());
                    final boolean keptLevel = fresh.recentOperations().stream()
                            .anyMatch(r -> r.status() == OperationRecord.STATUS_COMPLETED
                                    && r.priority() == dev.jstech.core.operation.OperationPriority.HIGH);
                    helper.assertTrue(keptLevel, "the resumed operation must keep the HIGH level it was given; recent="
                            + fresh.recentOperations());
                })
                .thenSucceed();
    }
}
