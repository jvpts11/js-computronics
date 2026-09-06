/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.datagen;

import dev.jstech.core.material.MaterialForm;
import dev.jstech.core.material.MaterialItems;
import dev.jstech.core.material.ModMaterial;
import dev.jstech.industrial.JsIndustrial;
import dev.jstech.industrial.recipe.MaceratingRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.concurrent.CompletableFuture;

/**
 * Generates the module's recipes: the Macerator's grinding recipes plus the vanilla cooking recipes
 * that smelt the resulting dust back into ingots, so macerating an ore drop genuinely doubles the
 * metal. All machine recipe ingredients use {@code c:} common tags for interop with other mods.
 */
public class JsIndustrialRecipeProvider extends RecipeProvider {

    public JsIndustrialRecipeProvider(final PackOutput output,
                                      final CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(final RecipeOutput recipeOutput) {
        // Ore doubling: both the ore block (c:ores/iron) and the raw drop accept two iron dust each,
        // and each dust smelts back into an ingot, so one mined ore becomes two ingots.
        macerating(recipeOutput, Ingredient.of(c("ores/iron")),
                new ItemStack(MaterialItems.get(ModMaterial.IRON, MaterialForm.DUST).get(), 2), "iron_ore_to_dust");
        macerating(recipeOutput, Ingredient.of(c("raw_materials/iron")),
                new ItemStack(MaterialItems.get(ModMaterial.IRON, MaterialForm.DUST).get(), 2), "raw_iron_to_dust");

        // Derived machine recipes (ingot->dust macerating, ingot->plate compressing) are generated for
        // every active (material, form) pair from the c: ingot tag, so any ingot from another mod works
        // as input and adding a new plate or dust later is a one-line change in ModMaterial.
        MaterialFormRecipes.generateAll(recipeOutput);

        // Smelting and blasting dust back into ingots. The ingredient uses the c:dusts/iron tag so
        // iron dust from other mods can also be smelted here.
        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(c("dusts/iron")),
                        RecipeCategory.MISC, Items.IRON_INGOT, 0.7F, 200)
                .unlockedBy("has_iron_dust", has(c("dusts/iron")))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(
                        JsIndustrial.MODID, "iron_ingot_from_smelting_iron_dust"));

        SimpleCookingRecipeBuilder.blasting(
                        Ingredient.of(c("dusts/iron")),
                        RecipeCategory.MISC, Items.IRON_INGOT, 0.7F, 100)
                .unlockedBy("has_iron_dust", has(c("dusts/iron")))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(
                        JsIndustrial.MODID, "iron_ingot_from_blasting_iron_dust"));
    }

    private static TagKey<Item> c(final String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static void macerating(final RecipeOutput recipeOutput, final Ingredient ingredient,
                                   final ItemStack result, final String name) {
        recipeOutput.accept(
                ResourceLocation.fromNamespaceAndPath(JsIndustrial.MODID, "macerating/" + name),
                new MaceratingRecipe(ingredient, result, 200),
                null);
    }
}
