/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.storage.StorageKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.EntityBlock;

import java.util.List;
import java.util.stream.Stream;

/**
 * The coarse category the Network Interactor's category filter sorts data into: what the item is by the
 * tags the mods agree on ({@code c:ingots}, {@code c:ores}...), else by what kind of item it is (a block, a
 * machine, a tool, armour, food), with fluids and chemicals as categories of their own.
 */
public final class ItemCategories {

    public static final String OTHER = "Other";

    /** The categories in the order the filter lists them. */
    public static final List<String> ALL = List.of("Ingots", "Nuggets", "Ores", "Raw materials", "Dusts", "Gems",
            "Plates", "Rods", "Blocks", "Machines", "Tools", "Armor", "Food", "Fluids", "Chemicals", OTHER);

    /** The common tag paths that name a category, and the category each names. */
    private static final String[][] TAGGED = {
            {"ingots", "Ingots"}, {"nuggets", "Nuggets"}, {"ores", "Ores"}, {"raw_materials", "Raw materials"},
            {"dusts", "Dusts"}, {"gems", "Gems"}, {"plates", "Plates"}, {"rods", "Rods"},
            {"storage_blocks", "Blocks"}, {"tools", "Tools"}, {"armors", "Armor"}, {"foods", "Food"}};

    private ItemCategories() {
    }

    public static String of(final StorageKey key) {
        if (key.isFluid()) {
            return "Fluids";
        }
        if (key.isChemical()) {
            return "Chemicals";
        }
        final ItemStack stack = key.stack(1);
        final String tagged = byTag(stack.getItemHolder().tags());
        if (tagged != null) {
            return tagged;
        }
        final Item item = stack.getItem();
        if (item instanceof BlockItem block) {
            return block.getBlock() instanceof EntityBlock ? "Machines" : "Blocks";
        }
        if (item instanceof DiggerItem || item instanceof SwordItem || item instanceof ShearsItem
                || item instanceof FishingRodItem || item instanceof FlintAndSteelItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof TridentItem) {
            return "Tools";
        }
        if (item instanceof ArmorItem) {
            return "Armor";
        }
        if (stack.has(DataComponents.FOOD)) {
            return "Food";
        }
        return OTHER;
    }

    private static String byTag(final Stream<TagKey<Item>> tags) {
        return tags.map(TagKey::location)
                .filter(id -> id.getNamespace().equals("c"))
                .map(ItemCategories::categoryOfTag)
                .filter(c -> c != null)
                .findFirst()
                .orElse(null);
    }

    private static String categoryOfTag(final ResourceLocation tag) {
        final String path = tag.getPath();
        for (final String[] entry : TAGGED) {
            if (path.equals(entry[0]) || path.startsWith(entry[0] + "/")) {
                return entry[1];
            }
        }
        return null;
    }
}
