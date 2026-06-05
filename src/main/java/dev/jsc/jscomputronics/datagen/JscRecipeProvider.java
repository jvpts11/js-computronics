/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.datagen;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import dev.jsc.jscomputronics.module.industrial.recipe.MaceratingRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.concurrent.CompletableFuture;

/**
 * Generates the module's recipes: the Macerator's grinding recipes plus the vanilla cooking recipes that smelt the resulting dust back into ingots, so macerating an ore drop genuinely doubles the metal.
 */
public class JscRecipeProvider extends RecipeProvider {

    public JscRecipeProvider(final PackOutput output,
                             final CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(final RecipeOutput recipeOutput) {
        // Ore doubling: macerating one raw iron yields two iron dust, and each
        // dust smelts back into an ingot, so one raw iron becomes two ingots.
        macerating(recipeOutput, Ingredient.of(Items.RAW_IRON),
                new ItemStack(IndustrialModule.IRON_DUST.get(), 2), "raw_iron");

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(IndustrialModule.IRON_DUST.get()),
                        RecipeCategory.MISC, Items.IRON_INGOT, 0.7F, 200)
                .unlockedBy("has_iron_dust", has(IndustrialModule.IRON_DUST.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(
                        JsComputronics.MODID, "iron_ingot_from_smelting_iron_dust"));

        SimpleCookingRecipeBuilder.blasting(
                        Ingredient.of(IndustrialModule.IRON_DUST.get()),
                        RecipeCategory.MISC, Items.IRON_INGOT, 0.7F, 100)
                .unlockedBy("has_iron_dust", has(IndustrialModule.IRON_DUST.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(
                        JsComputronics.MODID, "iron_ingot_from_blasting_iron_dust"));
    }

    private static void macerating(final RecipeOutput recipeOutput, final Ingredient ingredient,
                                   final ItemStack result, final String name) {
        recipeOutput.accept(
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "macerating/" + name),
                new MaceratingRecipe(ingredient, result, 200),
                null);
    }
}
