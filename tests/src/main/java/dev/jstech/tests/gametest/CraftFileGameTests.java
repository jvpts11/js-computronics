/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.crafting.CraftingPattern;
import dev.jstech.computers.hardware.DiskSize;
import dev.jstech.computers.hardware.StorageTier;
import dev.jstech.computers.os.FilesystemKind;
import dev.jstech.computers.os.fs.CraftFile;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.computers.os.fs.FileType;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Integration tests for saving and loading {@link CraftingPattern} instances to and from
 * {@code .craft} files on a Crafting Computer's system disk.
 *
 * <p>Tests drive the server-side logic directly (no client screen involved): they write patterns
 * via {@link CraftFile} + {@link DiskFilesystem}, and read them back, verifying round-trip
 * correctness.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CraftFileGameTests {

    private CraftFileGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    /**
     * Serializes a {@link CraftingPattern} to a {@code .craft} file, then parses it back
     * and asserts the patterns are equal. This validates both {@link CraftFile#serialize} and
     * {@link CraftFile#parse} end to end against a live registry.
     */
    @GameTest(template = ARENA)
    public static void craftFile_serializeAndParseRoundTrip(final GameTestHelper helper) {
        final CraftingPattern pattern = planksPattern(4);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final Optional<String> serialized =
                            CraftFile.serialize(pattern, helper.getLevel().registryAccess());
                    helper.assertTrue(serialized.isPresent(),
                            "CraftFile.serialize must succeed for a valid pattern");

                    final Optional<CraftingPattern> parsed =
                            CraftFile.parse(serialized.get(), helper.getLevel().registryAccess());
                    helper.assertTrue(parsed.isPresent(),
                            "CraftFile.parse must succeed on the serialized output");
                    helper.assertTrue(pattern.equals(parsed.get()),
                            "the parsed pattern must equal the original; result: " + parsed.get());
                })
                .thenSucceed();
    }

    /**
     * Writes a {@code .craft} file onto a disk, asserts it appears in {@link DiskFilesystem#list},
     * then reads and parses it back, and finally clears the pattern from the ROM and loads it
     * again from the file, verifying the full save → list → load round-trip.
     */
    @GameTest(template = ARENA)
    public static void craftFile_saveListLoadRoundTrip(final GameTestHelper helper) {
        final BlockPos ccPos = new BlockPos(2, 2, 2);
        final BlockPos mainframePos = new BlockPos(3, 2, 2);

        // Place a Crafting Computer and a Mainframe side by side so the CC can join a network.
        helper.setBlock(ccPos, ComputingModule.CRAFTING_COMPUTER.get());
        helper.setBlock(mainframePos, ComputingModule.MAINFRAME.get());

        if (!(helper.getBlockEntity(ccPos) instanceof CraftingComputerBlockEntity cc)) {
            helper.fail("no CraftingComputerBlockEntity at " + ccPos);
            return;
        }
        if (!(helper.getBlockEntity(mainframePos) instanceof MainframeBlockEntity mainframe)) {
            helper.fail("no MainframeBlockEntity at " + mainframePos);
            return;
        }

        // Install minimal hardware on the Crafting Computer: board + CPU + RAM + PSU + disk.
        final ItemStackHandler hw = cc.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        // The disk slot must have a disk before installing an OS (footprint check).
        hw.setStackInSlot(CraftingComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));

        // Install the Network OS onto the disk; FLAT filesystem (mc_net uses the DOS kernel).
        final ResourceLocation soRede =
                ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "mc_net");
        cc.installOs(soRede);

        // Load a pattern into the ROM.
        final CraftingPattern original = planksPattern(4);
        cc.loadPattern(original);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    /*
                     * SAVE
                     * Serialize the first ROM pattern and write it to the system disk.
                     */
                    final ItemStack sysDisk = cc.systemDisk();
                    helper.assertFalse(sysDisk.isEmpty(), "system disk must be present after installOs");

                    final Optional<String> serialized =
                            CraftFile.serialize(cc.romPatterns().get(0), helper.getLevel().registryAccess());
                    helper.assertTrue(serialized.isPresent(), "serialize must succeed");

                    // Write to the disk with ample free weight.
                    final DiskFilesystem.WriteResult writeResult = DiskFilesystem.write(
                            sysDisk, "test_recipe.craft", FileType.CRAFT, serialized.get(),
                            Long.MAX_VALUE, FilesystemKind.FLAT);
                    helper.assertTrue(writeResult == DiskFilesystem.WriteResult.OK,
                            "write must return OK; got: " + writeResult);
                    cc.setChanged();

                    // LIST
                    final List<DiskFilesystem.FileEntry> entries =
                            DiskFilesystem.list(sysDisk, "", FilesystemKind.FLAT);
                    final long craftCount = entries.stream()
                            .filter(e -> e.type() == FileType.CRAFT)
                            .count();
                    helper.assertTrue(craftCount == 1L,
                            "exactly one .craft entry must appear in list(); got: " + craftCount);
                    helper.assertTrue(DiskFilesystem.exists(sysDisk, "test_recipe.craft"),
                            "exists() must confirm the written file");

                    /*
                     * LOAD
                     * Clear the ROM so we can verify the load re-adds the pattern.
                     */
                    cc.removePattern(0);
                    helper.assertTrue(cc.romUsed() == 0, "ROM must be empty after removePattern");

                    // Read and parse the file.
                    final Optional<String> content = DiskFilesystem.read(sysDisk, "test_recipe.craft");
                    helper.assertTrue(content.isPresent(), "read must return the file content");

                    final Optional<CraftingPattern> loaded =
                            CraftFile.parse(content.get(), helper.getLevel().registryAccess());
                    helper.assertTrue(loaded.isPresent(), "parse must succeed on the stored content");

                    // Add it back to the ROM.
                    final boolean added = cc.loadPattern(loaded.get());
                    helper.assertTrue(added, "loadPattern must accept the parsed pattern");
                    helper.assertTrue(cc.romUsed() == 1, "ROM must hold exactly one pattern after load");

                    // The restored pattern must equal the original.
                    helper.assertTrue(cc.romPatterns().get(0).equals(original),
                            "the loaded pattern must equal the original");
                })
                .thenSucceed();
    }

    /**
     * Verifies that {@link CraftFile#parse} returns empty for corrupt or non-SNBT content,
     * leaving the system in a safe state.
     */
    @GameTest(template = ARENA)
    public static void craftFile_parseRejectsInvalidContent(final GameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final Optional<CraftingPattern> result =
                            CraftFile.parse("THIS IS NOT VALID SNBT", helper.getLevel().registryAccess());
                    helper.assertFalse(result.isPresent(),
                            "parse must return empty for non-SNBT content");

                    final Optional<CraftingPattern> result2 =
                            CraftFile.parse("{\"notAPattern\": 1}", helper.getLevel().registryAccess());
                    helper.assertFalse(result2.isPresent(),
                            "parse must return empty for valid JSON that is not a CraftingPattern");
                })
                .thenSucceed();
    }

    // Pattern fixtures

    private static CraftingPattern planksPattern(final int count) {
        final List<ItemStack> grid = emptyGrid();
        grid.set(0, new ItemStack(Items.OAK_LOG));
        return new CraftingPattern(grid, new ItemStack(Items.OAK_PLANKS, count));
    }

    private static List<ItemStack> emptyGrid() {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }
}
