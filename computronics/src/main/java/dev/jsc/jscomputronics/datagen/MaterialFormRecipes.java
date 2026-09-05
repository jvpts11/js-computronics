/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.datagen;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jstech.core.material.MaterialForm;
import dev.jstech.core.material.MaterialItems;
import dev.jstech.core.material.ModMaterial;
import dev.jsc.jscomputronics.module.industrial.recipe.CompressingRecipe;
import dev.jsc.jscomputronics.module.industrial.recipe.MaceratingRecipe;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;

/**
 * Loops over every active (material, form) pair and emits the appropriate machine recipe.
 *
 * <p>Adding a production route for a new form is a single entry in {@link #PRODUCTIONS}.
 * Activating that form for a material is a single addition to {@link ModMaterial#activeModForms()}.
 * No other datagen files need to change.
 *
 * <p>Special-case recipes (ore doubling, dust→ingot smelting) live in
 * {@link JscRecipeProvider} because they use fixed input tags or vanilla item outputs
 * that are not derivable from the material/form pair alone.
 */
public final class MaterialFormRecipes {

    @FunctionalInterface
    private interface FormRecipe {
        void generate(RecipeOutput out, ModMaterial mat, MaterialForm form);
    }

    private static final EnumMap<MaterialForm, FormRecipe> PRODUCTIONS = new EnumMap<>(MaterialForm.class);

    static {
        // Compress one ingot into one plate.
        PRODUCTIONS.put(MaterialForm.PLATE, (out, mat, form) ->
                out.accept(
                        rl("compressing/" + id(mat, MaterialForm.INGOT, form)),
                        new CompressingRecipe(
                                cTag(mat, MaterialForm.INGOT),
                                new ItemStack(MaterialItems.get(mat, form).get(), 1),
                                CompressingRecipe.DEFAULT_PROCESSING_TIME),
                        null));

        // Macerate one ingot into one dust.
        PRODUCTIONS.put(MaterialForm.DUST, (out, mat, form) ->
                out.accept(
                        rl("macerating/" + id(mat, MaterialForm.INGOT, form)),
                        new MaceratingRecipe(
                                cTag(mat, MaterialForm.INGOT),
                                new ItemStack(MaterialItems.get(mat, form).get(), 1),
                                200),
                        null));

        // BOLT, ROD, GEAR: production routes not yet established.
        // Add one entry here per form when input, machine and ratio are defined.
    }

    private MaterialFormRecipes() {}

    /** Generates all machine recipes that have a defined production route. */
    public static void generateAll(final RecipeOutput out) {
        for (final ModMaterial mat : ModMaterial.values()) {
            for (final MaterialForm form : mat.activeModForms()) {
                final FormRecipe recipe = PRODUCTIONS.get(form);
                if (recipe != null) {
                    recipe.generate(out, mat, form);
                }
            }
        }
    }

    private static String id(final ModMaterial mat, final MaterialForm inputForm, final MaterialForm outputForm) {
        return mat.materialName() + "_" + inputForm.name().toLowerCase()
                + "_to_" + outputForm.name().toLowerCase();
    }

    private static ResourceLocation rl(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, path);
    }

    private static Ingredient cTag(final ModMaterial mat, final MaterialForm inputForm) {
        return Ingredient.of(ItemTags.create(
                ResourceLocation.fromNamespaceAndPath("c", inputForm.tagPath(mat.materialName()))));
    }
}
