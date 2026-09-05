/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.testkit;

import dev.jstech.computronics.crafting.CraftingPattern;
import dev.jstech.computronics.crafting.MultiStagePattern;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.os.FilesystemKind;
import dev.jstech.computronics.os.fs.CraftFile;
import dev.jstech.computronics.os.fs.DiskFilesystem;
import dev.jstech.computronics.os.fs.FileType;
import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts recipe files on a medium the way the Pattern Encoder does once its job is through, without the ticks:
 * what a test needs when the recipe's journey is what it checks, not the burner. Also the common fixture
 * patterns tests share.
 */
public final class CraftFiles {

    /** The room a fresh medium has for files, generous enough for any test fixture. */
    private static final long FREE_WEIGHT = 1_000_000L;

    private CraftFiles() {
    }

    /** Writes {@code pattern} onto {@code media} under its result's name; returns the path it got. */
    public static String writeBench(final ItemStack media, final CraftingPattern pattern,
                                    final HolderLookup.Provider registries) {
        final String content = CraftFile.serialize(pattern, registries).orElseThrow();
        final String base = pattern.name().isEmpty()
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(pattern.result().getItem()).getPath()
                : pattern.name().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return write(media, base, content);
    }

    public static String writeProcessing(final ItemStack media, final ProcessingPattern pattern,
                                         final HolderLookup.Provider registries) {
        final String content = CraftFile.serializeProcessing(pattern, registries).orElseThrow();
        final ProcessingPattern.ProcessingOutput out = pattern.primaryOutput();
        final String base = out != null && out.key().isItem()
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.key().item()).getPath() : "machine";
        return write(media, base, content);
    }

    public static String writeMultiStage(final ItemStack media, final MultiStagePattern pattern,
                                         final HolderLookup.Provider registries) {
        final String content = CraftFile.serializeMultiStage(pattern, registries).orElseThrow();
        return write(media, "multistage", content);
    }

    private static String write(final ItemStack media, final String base, final String content) {
        final String path = DiskFilesystem.uniquePath(media, base, ".craft", content);
        final DiskFilesystem.WriteResult result = DiskFilesystem.write(media, path, FileType.CRAFT, content,
                FREE_WEIGHT, FilesystemKind.HIERARCHICAL, 0L);
        if (result != DiskFilesystem.WriteResult.OK) {
            throw new IllegalStateException("could not write " + path + ": " + result);
        }
        return path;
    }

    /** How many {@code .craft} files {@code media} holds. */
    public static int count(final ItemStack media) {
        int n = 0;
        for (final DiskFilesystem.FileEntry e : DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL)) {
            if (e.type() == FileType.CRAFT) {
                n++;
            }
        }
        return n;
    }

    /** One oak log to four planks: the bench recipe the recipe book resolves for a single log. */
    public static CraftingPattern oakPlanks() {
        final List<ItemStack> grid = emptyGrid();
        grid.set(0, new ItemStack(Items.OAK_LOG));
        return new CraftingPattern(grid, new ItemStack(Items.OAK_PLANKS, 4));
    }

    /** Raw iron to an ingot in a furnace. */
    public static ProcessingPattern furnaceIron(final int timeoutTicks) {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.RAW_IRON), 1, false)),
                List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(Items.IRON_INGOT), 1,
                        ProcessingPattern.FULL_CHANCE)),
                "minecraft:furnace", timeoutTicks);
    }

    public static List<ItemStack> emptyGrid() {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }
}
