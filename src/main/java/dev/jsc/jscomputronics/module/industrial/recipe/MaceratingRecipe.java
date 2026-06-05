/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * A grinding recipe for the Macerator: one input item is consumed and the result is produced after {@code processingTime} ticks.
 */
public record MaceratingRecipe(Ingredient ingredient, ItemStack result, int processingTime)
        implements Recipe<SingleRecipeInput> {

    @Override
    public boolean matches(final SingleRecipeInput input, final Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(final SingleRecipeInput input, final HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(final HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return IndustrialModule.MACERATING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return IndustrialModule.MACERATING_TYPE.get();
    }

    /**
     * Codec-based serializer (persistence + network) for {@link MaceratingRecipe}.
     */
    public static final class Serializer implements RecipeSerializer<MaceratingRecipe> {

        public static final MapCodec<MaceratingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(MaceratingRecipe::ingredient),
                        ItemStack.CODEC.fieldOf("result").forGetter(MaceratingRecipe::result),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("processing_time", 200)
                                .forGetter(MaceratingRecipe::processingTime)
                ).apply(instance, MaceratingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, MaceratingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, MaceratingRecipe::ingredient,
                        ItemStack.STREAM_CODEC, MaceratingRecipe::result,
                        ByteBufCodecs.VAR_INT, MaceratingRecipe::processingTime,
                        MaceratingRecipe::new);

        @Override
        public MapCodec<MaceratingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MaceratingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
